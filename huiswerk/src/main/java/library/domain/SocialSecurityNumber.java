package library.domain;

public abstract class SocialSecurityNumber {
    private String number;

    public SocialSecurityNumber(String nummer) {
        this.number = nummer;
    }

    public String getSsn() {
        return this.number;
    }

    public void setSsn(String nummer) {
        this.number = nummer;
    }

    abstract boolean validateSSN(String nummer);
}
