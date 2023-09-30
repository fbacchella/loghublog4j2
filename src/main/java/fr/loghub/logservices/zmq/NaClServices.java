package fr.loghub.logservices.zmq;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.zeromq.ZConfig;
import org.zeromq.ZMQ;

import fr.loghub.naclprovider.NaclPrivateKeySpec;
import fr.loghub.naclprovider.NaclProvider;
import fr.loghub.naclprovider.NaclPublicKeySpec;

class NaClServices {

    private final KeyPairGenerator generator;
    private final KeyFactory kf;

    NaClServices() {
        try {
            Provider provider = new NaclProvider();
            generator = KeyPairGenerator.getInstance(NaclProvider.NAME, provider);
            kf = KeyFactory.getInstance(NaclProvider.NAME, provider);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    byte[] readPrivateKey(String path) {
        try {
            byte[] key = Files.readAllBytes(Paths.get(path));
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(key);
            PrivateKey pv = kf.generatePrivate(keySpec);
            NaclPrivateKeySpec naclspec = kf.getKeySpec(pv, NaclPrivateKeySpec.class);
            return naclspec.getBytes();
        } catch (IOException | InvalidKeySpecException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    byte[] writePair(String privatePath) {
        Pattern filePattern = Pattern.compile("(.*?)(\\.[a-zA-Z0-9]+)?$");
        Matcher m = filePattern.matcher(privatePath);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid file path for the curve secret key: " + privatePath);
        }
        String fileRadix = m.group(1);
        String fileExtension = m.group(2) != null ? m.group(2) : ".p8";
        Path privateKeyPath = Paths.get(fileRadix + fileExtension);
        try {
            generator.initialize(256);
            KeyPair kp = generator.generateKeyPair();
            PrivateKey pv = kp.getPrivate();
            Files.write(privateKeyPath, pv.getEncoded());

            PublicKey pb = kp.getPublic();
            NaclPublicKeySpec naclpubspec = kf.getKeySpec(pb, NaclPublicKeySpec.class);

            // Building the pub file
            String publicEncoded = String.format("Curve %s%n",
                    Base64.getEncoder().encodeToString(naclpubspec.getBytes()));
            Files.write(Paths.get(fileRadix + ".pub"), publicEncoded.getBytes(StandardCharsets.US_ASCII));

            // Building the zpl file
            ZConfig zconf = new ZConfig("root", null);
            zconf.putValue("curve/public-key", ZMQ.Curve.z85Encode(naclpubspec.getBytes()));
            zconf.save(fileRadix + ".zpl");
            return naclpubspec.getBytes();
        } catch (IOException | InvalidKeySpecException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
}
