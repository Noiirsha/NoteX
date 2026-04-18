package com.notex.domain;


import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

@Data
@Slf4j
public class Response<DataType> {

    private HttpStatus code;
    private String message;
    private DataType data;

    private Response(){};

    private Response(HttpStatus code, String message, DataType data){
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <DataType> Response<DataType> success(DataType data) {
        return new Response<>(HttpStatus.OK, "success", data);
    }

    public static <DataType> Response<DataType> success(String message, DataType data) {
        return new Response<>(HttpStatus.OK, message, data);
    }

    public static Response<Void> success() {
        return new Response<>(HttpStatus.OK, "success", null);
    }

    public static <DataType> Response<DataType> error(HttpStatus code, String message, DataType data) {
        return new Response<>(code, message, data);
    }

    public static <DataType> Response<DataType> error(HttpStatus code, String message) {
        return new Response<>(code, message, null);
    }

    public static <DataType> Response<DataType> error(String message) {
        return new Response<>(HttpStatus.INTERNAL_SERVER_ERROR, message, null);
    }


}
