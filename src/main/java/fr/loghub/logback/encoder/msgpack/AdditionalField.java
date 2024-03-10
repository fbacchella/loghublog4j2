package fr.loghub.logback.encoder.msgpack;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Data
public class AdditionalField {

    private String name;
    private String value;

}
