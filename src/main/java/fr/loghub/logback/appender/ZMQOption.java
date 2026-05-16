package fr.loghub.logback.appender;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Data
public class ZMQOption {
    private String name;
    private String value;
}
