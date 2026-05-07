package library.datastorage;

public class JSONMemberFactory implements IMemberFactory {
    private final IMemberDAO memberDAO;

    public JSONMemberFactory() {
        this.memberDAO = new JSONMemberDAO();
    }

    public IMemberDAO GetMemberDAO() {
        return this.memberDAO;
    }
}
