package library.datastorage;

import library.domain.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class MySQLMemberDAO implements IMemberDAO {

    private ArrayList<Member> members;
    private ArrayList<Book> books;

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

        Member aNewMember = new Member(1000, "Henk", "Hopper, de");

        // Initialize a calendar object with today as the current day.
        Calendar c = Calendar.getInstance();
        Date dateToday = new Date();
        c.setTime(dateToday);
        c.add(Calendar.DATE, 1);

        aNewMember.addLoan(new Loan(aNewMember, copyMulisch, c.getTime()));
        members.add(aNewMember);

        aNewMember = new Member(1001, "Mara", "Machtig");
        members.add(aNewMember);
        c.setTime(dateToday);
        c.add(Calendar.DATE, 8);
        aNewMember.addLoan(new Loan(aNewMember, copyIntoTheWild1, c.getTime()));
        Reservation aReservation = new Reservation(aNewMember, bookMulisch);
        c.setTime(dateToday);
        aReservation.setReservationDate(c.getTime());
        aNewMember.addReservation(aReservation);

        aNewMember = new Member(1002, "Kees", "Kerfstok");
        members.add(aNewMember);
        aReservation = new Reservation(aNewMember, bookMulisch);
        c.setTime(dateToday);
        aReservation.setReservationDate(c.getTime());
        aNewMember.addReservation(aReservation);

        aNewMember = new Member(1003, "Anna", "Nas");
        aNewMember.setFine(5.25);
        members.add(aNewMember);

        members.add(new Member(1004, "Cor", "Netto"));
    }

    @Override
    public Member FindMember(int identifier) {
        throw new UnsupportedOperationException(
                "Method not yet implemented"
        );
    }

    @Override
    public boolean DeleteMember(Member member) {
        throw new UnsupportedOperationException(
                "Method not yet implemented"
        );
    }
}
