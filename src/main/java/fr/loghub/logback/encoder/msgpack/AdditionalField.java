package fr.loghub.logback.encoder.msgpack;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Data
public class AdditionalField {

    @Setter
    private String name;
    @Setter
    private String value;

}
