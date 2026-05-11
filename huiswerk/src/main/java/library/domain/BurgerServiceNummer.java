package library.domain;

public class BurgerServiceNummer extends SocialSecurityNumber {
    public BurgerServiceNummer(String nummer) {
        super(nummer);
    }

    @Override
    boolean validateSSN(String nummer) {
        return true;
    }
}
