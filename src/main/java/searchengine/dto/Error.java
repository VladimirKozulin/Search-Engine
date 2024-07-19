package searchengine.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Error extends Response{
    String error;

    public Error(String error){
        this.setResult(false);
        this.error = error;
    }
}
