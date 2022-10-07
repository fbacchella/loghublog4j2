package fr.loghub.logback.encoder.msgpack;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class AdditionalField {

    @Getter @Setter
    private String name;
    @Getter @Setter
    private String value;

}
