/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package library.main;

import library.applicationlogic.MemberAdminManager;
import library.datastorage.IMemberFactory;
import library.datastorage.JSONMemberFactory;
import library.datastorage.MySQLMemberFactory;
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
        IMemberFactory iMemberFactory1 = new JSONMemberFactory();
        IMemberFactory iMemberFactory2 = new MySQLMemberFactory(); // for testing whether it is implemented
        MemberAdminManager manager = new MemberAdminManager(iMemberFactory1);
        MemberAdminUI ui = new MemberAdminUI(manager);
        SwingUtilities.invokeLater(ui);
    }
}
