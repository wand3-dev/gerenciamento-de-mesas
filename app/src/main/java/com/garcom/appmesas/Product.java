package com.garcom.appmesas;

public class Product {
    private String codigo;
    private String descricao;

    public Product(String codigo, String descricao) {
        this.codigo = codigo;
        this.descricao = descricao;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescricao() {
        return descricao;
    }
}
