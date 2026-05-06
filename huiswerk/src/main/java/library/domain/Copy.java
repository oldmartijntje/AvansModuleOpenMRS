/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package library.domain;

/**
 *
 * @author ppthgast
 */
public class Copy {
    
    private int copyID;
    private int lendingPeriod;
    
    // null in case Copy not lent.
    private Loan loan;
    
    private Book book;
    
    public Copy(int copyID, int lendingPeriod, Book book)
    {
        this.copyID = copyID;
        this.lendingPeriod = lendingPeriod;
        this.book = book;
        
        loan = null;
    }
    
    public int getCopyID()
    {
        return copyID;
    }
    
    public int getLendingPeriod()
    {
        return lendingPeriod;
    }
    
    public Book getBook()
    {
        return book;
    }
    
    public void setLoan(Loan newLoan)
    {
        loan = newLoan;
    }
    
    public Loan getLoan()
    {
        return loan;
    }
    
    public boolean isLent()
    {
        return loan != null;
    }

    public String toString()
    {
        return book + "\n" +
                "   " + copyID + " - " + "uitleenperiode " + lendingPeriod + "dag(en)";
    }
}
