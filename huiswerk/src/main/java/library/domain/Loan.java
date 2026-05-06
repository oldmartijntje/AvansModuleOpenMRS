/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package library.domain;

import java.util.Date;

/**
 *
 * @author ppthgast
 */
public class Loan {
    
    private Date returnDate;
    
    private Member member;
    private Copy copy;
    
    /**
     * Constructor for Loan. Member and Copy are parameters for this constructor, because these are mandatory
     * for a Loan.
     * 
     * @param member owner of the Loan
     * @param copy the Copy of the book the Member has lent
     * @param returnDate date on which the copy needs te be returned
     */
    public Loan(Member member, Copy copy, Date returnDate)
    {
        this.member = member;
        this.copy = copy;
        this.returnDate = returnDate;
    }
    
    public Member getMember()
    {
        return member;
    }

    public Copy getCopy()
    {
        return copy;
    }
    
    public Date getReturnDate()
    {
        return returnDate;
    }

    public String toString()
    {
        return copy.toString() + "\n" +
                "Retourdatum: " + returnDate;
    }
}
