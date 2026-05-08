package library.domain;

public class GermanSocialSecurityNumber extends SocialSecurityNumber {
    public GermanSocialSecurityNumber(String nummer) {
        super(nummer);
    }

    @Override
    boolean validateSSN(String nummer) {
        return true;
    }
}
