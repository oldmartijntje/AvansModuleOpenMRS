package library.domain;

public class BurgerServiceNummer {
    private String bsn;
    public BurgerServiceNummer(String nummer) {
        this.bsn = nummer;
    }

    public String getBsn() {
        return bsn;
    }

    public void setBsn(String nummer) {
        this.bsn = nummer;
    }

    private boolean ValidateBSN(String nummer) {
        // insert 11-proef here.
        return true;
    }
}
