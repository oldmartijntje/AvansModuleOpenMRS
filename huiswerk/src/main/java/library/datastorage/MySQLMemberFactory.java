package library.datastorage;

public class MySQLMemberFactory implements IMemberFactory {
    private final IMemberDAO memberDAO;

    public MySQLMemberFactory() {
        this.memberDAO = new MySQLMemberDAO();
    }

    public IMemberDAO GetMemberDAO() {
        return this.memberDAO;
    }
}
