/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package library.main;

import library.applicationlogic.MemberAdminManager;
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
        
        MemberAdminManager manager = new MemberAdminManager();
        MemberAdminUI ui = new MemberAdminUI(manager);
        SwingUtilities.invokeLater(ui);
    }
}
