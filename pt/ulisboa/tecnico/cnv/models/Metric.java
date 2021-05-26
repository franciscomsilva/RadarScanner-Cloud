package pt.ulisboa.tecnico.cnv.models;


public class Metric {
    int i_count;
    int load_count;
    int new_array;
    int new_count;
    int store_count;

    public Metric( int i_count, int load_count, int new_array, int new_count, int store_count) {
        this.i_count = i_count;
        this.load_count = load_count;
        this.new_array = new_array;
        this.new_count = new_count;
        this.store_count = store_count;
    }


    public int getI_count() {
        return i_count;
    }

    public void setI_count(int i_count) {
        this.i_count = i_count;
    }

    public int getLoad_count() {
        return load_count;
    }

    public void setLoad_count(int load_count) {
        this.load_count = load_count;
    }

    public int getNew_array() {
        return new_array;
    }

    public void setNew_array(int new_array) {
        this.new_array = new_array;
    }

    public int getNew_count() {
        return new_count;
    }

    public void setNew_count(int new_count) {
        this.new_count = new_count;
    }

    public int getStore_count() {
        return store_count;
    }

    public void setStore_count(int store_count) {
        this.store_count = store_count;
    }
}