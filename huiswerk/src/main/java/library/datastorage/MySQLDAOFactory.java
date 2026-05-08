package library.datastorage;

public class MySQLDAOFactory implements IDAOFactory {
    private final IMemberDAO memberDAO;

    public MySQLDAOFactory() {
        this.memberDAO = new MySQLMemberDAO();
    }

    public IMemberDAO GetMemberDAO() {
        return this.memberDAO;
    }
}
