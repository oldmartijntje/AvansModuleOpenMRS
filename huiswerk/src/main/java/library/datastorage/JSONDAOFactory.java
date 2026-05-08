package library.datastorage;

public class JSONDAOFactory implements IDAOFactory {
    private final IMemberDAO memberDAO;

    public JSONDAOFactory() {
        this.memberDAO = new JSONMemberDAO();
    }

    public IMemberDAO GetMemberDAO() {
        return this.memberDAO;
    }
}
