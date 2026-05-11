/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package library.applicationlogic;

import library.datastorage.IDAOFactory;
import library.domain.*;

/**
 *
 * @author ppthgast
 */
public class MemberAdminManager {

    private IDAOFactory iMemberFactory;
    public MemberAdminManager(IDAOFactory iMemberFactory)
    {
        this.iMemberFactory = iMemberFactory;
    }
    
    public Member findMember(int membershipNumber)
    {
        return iMemberFactory.GetMemberDAO().FindMember(membershipNumber);
//        Member member = null;
//
//        int index = 0;
//
//        while(member == null && index < members.size())
//        {
//            Member currentMember = members.get(index);
//
//            if(currentMember.getMembershipNumber() == membershipNumber)
//            {
//                // Found the member!
//                member = currentMember;
//            }
//            else
//            {
//                // Not the correct member, try the next one in the list.
//                index++;
//            }
//        }
//
//        return member;
    }
    
    public boolean removeMember(Member member)
    {
        return iMemberFactory.GetMemberDAO().DeleteMember(member);
//        boolean result = false;
//
//        if(member.isRemovable())
//        {
//            result = member.remove();
//        }
//        else
//        {
//            result = false;
//        }
//
//        return result;
    }
}
