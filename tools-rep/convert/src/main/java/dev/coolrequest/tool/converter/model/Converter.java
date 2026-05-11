package dev.coolrequest.tool.converter.model;

@FunctionalInterface
public interface Converter {
    String convert(String input) throws Exception;
}
