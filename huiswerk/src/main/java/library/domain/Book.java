/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package library.domain;

import java.util.ArrayList;

/**
 *
 * @author ppthgast
 */
public class Book {
    
    private String ISBN;
    private String title;
    private String author;
    private int edition;
    
    private ArrayList<Copy> copies;
    private ArrayList<Reservation> reservations;
    
    public Book(String ISBN, String title, String author, int edition)
    {
        this.ISBN = ISBN;
        this.title = title;
        this.author = author;
        this.edition = edition;
        
        copies = new ArrayList();
        reservations = new ArrayList();
    }

    public void addCopy(Copy newCopy)
    {
        copies.add(newCopy);
    }

    public String getISBN()
    {
        return ISBN;
    }

    public String getAuthor()
    {
        return author;
    }

    public int getEdition()
    {
        return edition;
    }

    public String getTitle()
    {
        return title;
    }
    
    public void removeReservation(Reservation reservation)
    {
        reservations.remove(reservation);
    }
    
    @Override
    public boolean equals(Object o)
    {
        boolean equal = false;
        
        if(o == this)
        {
            // Equal instances of this class.
            equal = true;
        }
        else
        {
            if(o instanceof Book)
            {
                Book b = (Book)o;
                
                // Book is identified by ISBN; checking on this attribute only will suffice.
                equal = ISBN.equals(b.ISBN);
            }
        }
        
        return equal;
    }
    
    @Override
    public int hashCode()
    {
        // This implementation is based on the best practice as described in Effective Java,
        // 2nd edition, Joshua Bloch.

        // ISBN is unique, so sufficient to be used as hashcode.
        int result = 17;
        result = 31 * result + ISBN.hashCode();
        result = 31 * result + title.hashCode();
        result = 31 * result + author.hashCode();
        result = 31 * result + edition;

        return result;
    }

    public String toString()
    {
        return title + ", " + author + ", editie " + edition + ", " + ISBN;
    }
}
