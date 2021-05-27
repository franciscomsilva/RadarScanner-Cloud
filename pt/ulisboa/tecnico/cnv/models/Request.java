package pt.ulisboa.tecnico.cnv.models;


public class Request {
    String metrics_id;
    String id;
    int area;
    int height;
    int width;
    String map_image;
    String scan_type;

    public Request( String id,String metrics_id, int area, int height, int width, String map_image, String scan_type) {
        this.id = id;
        this.metrics_id = metrics_id;
        this.area = area;
        this.height = height;
        this.width = width;
        this.map_image = map_image;
        this.scan_type = scan_type;
    }

    public Request(){

    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMetrics_id() {
        return metrics_id;
    }

    public void setMetrics_id(String metrics_id) {
        this.metrics_id = metrics_id;
    }

    public int getArea() {
        return area;
    }

    public void setArea(int area) {
        this.area = area;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public String getMap_image() {
        return map_image;
    }

    public void setMap_image(String map_image) {
        this.map_image = map_image;
    }

    public String getScan_type() {
        return scan_type;
    }

    public void setScan_type(String scan_type) {
        this.scan_type = scan_type;
    }

    public String toString() {
        return this.metrics_id + "," + this.area + "," + this.width + "," + this.height + "," + this.map_image + "," + this.scan_type;
    }
}