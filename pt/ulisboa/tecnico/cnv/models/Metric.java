package pt.ulisboa.tecnico.cnv.models;


public class Metric {
    String id;
    long i_count;
    long load_count;
    long store_count;

    public Metric( String id,long i_count, long load_count, long store_count) {
        this.id = id;
        this.i_count = i_count;
        this.load_count = load_count;
        this.store_count = store_count;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getI_count() {
        return i_count;
    }

    public void setI_count(int i_count) {
        this.i_count = i_count;
    }

    public long getLoad_count() {
        return load_count;
    }

    public void setLoad_count(int load_count) {
        this.load_count = load_count;
    }

    public long getStore_count() {
        return store_count;
    }

    public void setStore_count(int store_count) {
        this.store_count = store_count;
    }
}