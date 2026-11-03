package games.strategy.engine.framework.startup.ui.posted.game;

import lombok.experimental.UtilityClass;

/**
 * Stores help text constants and helper methods to create help texts used in the PBF and PBEM
 * screens.
 */
@UtilityClass
public class HelpTexts {

  public static final String TRIPLEA_FORUM =
      "<html><p style='width: 400px;'>"
          + "Posts every turn to forums.triplea-game.org, as you.<br/>"
          + "1. Sign in once on Multiplayer &gt; Play by forum.<br/>"
          + "2. Start a new thread for the game: TripleA starts it in the Play by Forum "
          + "category https://forums.triplea-game.org/category/45/ when the game starts. Or use "
          + "an existing thread: paste its address, eg "
          + "https://forums.triplea-game.org/topic/24/my-game, or type its number (24).<br/>"
          + "3. At the end of your turn, TripleA posts the summary and the save game there.<br/>"
          + "<i>Note:</i> The forum shows the threads and posts of new members once a "
          + "moderator approved them. Your sign-in is not part of the save game; it is stored "
          + "encrypted on this computer."
          + "</p></html>";
  public static final String AXIS_AND_ALLIES_DOT_ORG_FORUM =
      "<html><p style='width: 400px;'>"
          + "Posts to www.AxisAndAllies.org<br/>"
          + "This poster is build for PBEM games via AxisAndAllies.org. Instructions:"
          + "<br/>"
          + "Sign in on Multiplayer &gt; Play by forum, or enter your login here.<br/>"
          + "Create a new Forum post in the Play Boardgames section "
          + "http://www.axisandallies.org/forums/index.php?board=40.0<br/>"
          + "Paste its address from the address bar of your browser into the thread field, "
          + "or type its topic number.<br/>"
          + "Without signing in, put your username and password for the AxisAndAllies.org "
          + "forum into the username and password fields<br/>"
          + "Click the Test Post button, to check that TripleA can reply to your forum "
          + "post<br/>"
          + "<i>Note:</i> Your AxisAndAllies.org username and password are not stored as part "
          + "of the save game, but they are stored encrypted in the local file system "
          + "if you select the option to remember your credentials. You may have to "
          + "enter your username and password again if you open the save game on "
          + "another computer."
          + "</p></html>";
  public static final String SMTP_DISABLED =
      "<html><p style='width: 400px;'>"
          + "Email sender<br/>"
          + "An email sender can email the turn summary and save game to multiple "
          + "recipients at the end of each players turn. This allows two or more "
          + "players to play a game where you don't have to be online at the same "
          + "time.<br/>"
          + "Each email sender may require custom configuration, to learn more "
          + "click help again after selecting a specific email sender."
          + "</p></html>";

  public static final String GENERIC_SMTP_SERVER =
      "<html><p style='width: 400px;'>"
          + "Email through SMTP<br/>"
          + "This email sends email via any generic SMTP service. Configuration:<br/>"
          + "<b>Subject:</b> This will be the subject of the email. In addition to the text "
          + "entered, the player and round number will be appended<br/>"
          + "<b>To:</b> A list of email addresses separated by space. the email will be sent "
          + "to all these users<br/>"
          + "<b>Email Username:</b>: Your login to the SMTP server<br/>"
          + "<b>Email Password:</b> Your password SMTP server<br/>"
          + "<b>SMTP Server:</b> The host address (or ip) of the SMTP server<br/>"
          + "<b>Port:</b> The port of the SMTP server (typically 25 for unencrypted, and 587 "
          + "for TLS encrypted servers)<br/>"
          + "Use TLS encryption: check this if yor server supports the TLS protocol for "
          + "email encryption<br/>"
          + "<i>Note:</i> Your SMTP login and password are not stored as part of the save game, "
          + "but they are stored encrypted in the local file system if you select the "
          + "option to remember your credentials.<br/>"
          + "You may have to enter your login and password again if you open the save "
          + "game on another computer."
          + "</p></html>";

  public static String gmailHelpText() {
    return emailProviderHelpText("Gmail", "Gmails");
  }

  public static String hotmailHelpText() {
    return emailProviderHelpText("Hotmail", "Hotmails (live.com)");
  }

  private static String emailProviderHelpText(final String name, final String possisiveName) {
    return String.format(
        "<html><p style='width: 400px;'>"
            + "Email through %s<br/>"
            + "This email sends email via %s SMTP service. To use this "
            + "you must have a %s account. Configuration:<br/>"
            + "<b>Subject:</b> This will be the subject of the email. In addition to the text "
            + "entered, the player and round number will be appended<br/>"
            + "<b>To:</b> A list of email addresses separated by space. the email will be sent to "
            + "all these users<br/>"
            + "<b>Email Username:</b> Your %s login used to authenticate against the %s SMTP "
            + "service<br/>"
            + "<b>Email Password:</b> Your %s password used to authenticate against the %s "
            + "SMTP service<br/>"
            + "<i>Note:</i> All communication with the %s service uses TLS encryption. Your "
            + "%s login and password are not stored as part of the save game, but "
            + "they are stored encrypted in the local file system if you select the option "
            + "to remember your credentials.<br/>"
            + "You may have to enter your login and password again if you open the save "
            + "game on another computer."
            + "</p></html>",
        name, possisiveName, name, name, name, name, name, name, name);
  }

  public static String rememberPlayByForumPassword() {
    return rememberPassword("play-by-forum");
  }

  public static String rememberPlayByEmailPassword() {
    return rememberPassword("play-by-email");
  }

  private static String rememberPassword(final String playTypeLabel) {
    return String.format(
        "<html>Remembering your password will store your password on your<br>"
            + "computer in an encrypted format. Otherwise you will need to enter your password<br>"
            + "whenever you start a %s game. While difficult, a person or virus<br>"
            + "with full access to your system could decrypt a remembered password.",
        playTypeLabel);
  }
}
