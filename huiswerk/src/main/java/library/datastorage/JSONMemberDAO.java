package library.datastorage;

import library.domain.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class JSONMemberDAO implements IMemberDAO {
    private ArrayList<Member> members;
    private ArrayList<Book> books;

    JSONMemberDAO() {
        Book aBook = new Book("978-0345803481", "Fifty Shades of Grey", "E L James", 1);
        aBook.addCopy(new Copy(10001, 20, aBook));
        aBook.addCopy(new Copy(10002, 20, aBook));
        books.add(aBook);

        aBook = new Book("9780330351690", "Into the Wild", "J Krakauer", 3);
        Copy copyIntoTheWild1 = new Copy(10003, 20, aBook);
        aBook.addCopy(copyIntoTheWild1);
        aBook.addCopy(new Copy(10004, 10, aBook));
        books.add(aBook);

        Book bookMulisch = new Book("\t978-90-234-2822-0", "De ontdekking van de Hemel", "H Mulisch", 24);
        Copy copyMulisch = new Copy(10005, 30, bookMulisch);
        aBook.addCopy(copyMulisch);
        books.add(bookMulisch);

        Member aNewMember = new Member(1000, "Arno", "Broeders");

        // Initialize a calendar object with today as the current day.
        Calendar c = Calendar.getInstance();
        Date dateToday = new Date();
        c.setTime(dateToday);
        c.add(Calendar.DATE, 1);

        aNewMember.addLoan(new Loan(aNewMember, copyMulisch, c.getTime()));
        members.add(aNewMember);

        aNewMember = new Member(1001, "Hans", "Linden, van der");
        members.add(aNewMember);
        c.setTime(dateToday);
        c.add(Calendar.DATE, 8);
        aNewMember.addLoan(new Loan(aNewMember, copyIntoTheWild1, c.getTime()));
        Reservation aReservation = new Reservation(aNewMember, bookMulisch);
        c.setTime(dateToday);
        aReservation.setReservationDate(c.getTime());
        aNewMember.addReservation(aReservation);

        aNewMember = new Member(1002, "Marc", "Mathijssen");
        members.add(aNewMember);
        aReservation = new Reservation(aNewMember, bookMulisch);
        c.setTime(dateToday);
        aReservation.setReservationDate(c.getTime());
        aNewMember.addReservation(aReservation);

        aNewMember = new Member(1003, "Robin", "Schellius");
        aNewMember.setFine(5.25);
        members.add(aNewMember);

        members.add(new Member(1004, "Marcel", "Groot, de"));
    }

    public Member FindMember(int identifier) {
        throw new UnsupportedOperationException(
                "Method not yet implemented"
        );
    }

    public boolean DeleteMember(Member member) {
        throw new UnsupportedOperationException(
                "Method not yet implemented"
        );
    }
}
