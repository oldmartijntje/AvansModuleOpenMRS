package library.datastorage;

import library.domain.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class MySQLMemberDAO implements IMemberDAO {
    private ArrayList<Member> members = new ArrayList<>();
    private ArrayList<Book> books = new ArrayList<>();

    MySQLMemberDAO() {
        Book aBook = new Book("978-0345803481", "Project Hail Mary", "Andy Weir", 1);
        aBook.addCopy(new Copy(10001, 20, aBook));
        aBook.addCopy(new Copy(10002, 20, aBook));
        books.add(aBook);

        aBook = new Book("9780330351690", "The Maze Runner", "James Dashner", 3);
        Copy copyIntoTheWild1 = new Copy(10003, 20, aBook);
        aBook.addCopy(copyIntoTheWild1);
        aBook.addCopy(new Copy(10004, 10, aBook));
        books.add(aBook);

        Book bookMulisch = new Book("\t978-90-234-2822-0", "The Hitchhiker's Guide to the Galaxy", "Douglas Adams", 24);
        Copy copyMulisch = new Copy(10005, 30, bookMulisch);
        aBook.addCopy(copyMulisch);
        books.add(bookMulisch);

        Member aNewMember = new Member(1000, "Henk", "Hopper, de", "fake-id" , "NL");

        // Initialize a calendar object with today as the current day.
        Calendar c = Calendar.getInstance();
        Date dateToday = new Date();
        c.setTime(dateToday);
        c.add(Calendar.DATE, 1);

        aNewMember.addLoan(new Loan(aNewMember, copyMulisch, c.getTime()));
        members.add(aNewMember);

        aNewMember = new Member(1001, "Mara", "Machtig", "fake-id", "DE");
        members.add(aNewMember);
        c.setTime(dateToday);
        c.add(Calendar.DATE, 8);
        aNewMember.addLoan(new Loan(aNewMember, copyIntoTheWild1, c.getTime()));
        Reservation aReservation = new Reservation(aNewMember, bookMulisch);
        c.setTime(dateToday);
        aReservation.setReservationDate(c.getTime());
        aNewMember.addReservation(aReservation);

        aNewMember = new Member(1002, "Kees", "Kerfstok", "fake-id", "BE");
        members.add(aNewMember);
        aReservation = new Reservation(aNewMember, bookMulisch);
        c.setTime(dateToday);
        aReservation.setReservationDate(c.getTime());
        aNewMember.addReservation(aReservation);

        aNewMember = new Member(1003, "Anna", "Nas", "fake-id", "DE");
        aNewMember.setFine(5.25);
        members.add(aNewMember);

        members.add(new Member(1004, "Cor", "Netto", "fake-id", "NL"));
    }

    public Member FindMember(int identifier) {
        Member member = null;

        int index = 0;

        while(member == null && index < members.size())
        {
            Member currentMember = members.get(index);

            if(currentMember.getMembershipNumber() == identifier)
            {
                // Found the member!
                member = currentMember;
            }
            else
            {
                // Not the correct member, try the next one in the list.
                index++;
            }
        }

        return member;
    }

    public boolean DeleteMember(Member member) {
        boolean result = false;

        if(member.isRemovable())
        {
            result = member.remove();
        }
        else
        {
            result = false;
        }

        return result;
    }
}
