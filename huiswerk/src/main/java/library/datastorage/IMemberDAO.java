package library.datastorage;

import library.domain.Member;

public interface IMemberDAO {
    public Member FindMember(int identifier);
    public boolean DeleteMember(Member member);
}
