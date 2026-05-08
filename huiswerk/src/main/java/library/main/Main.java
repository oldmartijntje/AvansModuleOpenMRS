/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package library.main;

import library.applicationlogic.MemberAdminManager;
import library.datastorage.IDAOFactory;
import library.datastorage.JSONDAOFactory;
import library.datastorage.MySQLDAOFactory;
import library.presentation.MemberAdminUI;

import javax.swing.*;

/**
 *
 * @author ppthgast
 */
public class Main {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        IDAOFactory iMemberFactory1 = new JSONDAOFactory();
        IDAOFactory iMemberFactory2 = new MySQLDAOFactory(); // for testing whether it is implemented
        MemberAdminManager manager = new MemberAdminManager(iMemberFactory1);
        MemberAdminUI ui = new MemberAdminUI(manager);
        SwingUtilities.invokeLater(ui);
    }
}
