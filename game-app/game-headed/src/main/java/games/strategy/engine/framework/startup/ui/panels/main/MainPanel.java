package games.strategy.engine.framework.startup.ui.panels.main;

import games.strategy.engine.chat.ChatPanel;
import games.strategy.engine.framework.HtmlUtils;
import games.strategy.engine.framework.I18nEngineFramework;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.startup.ui.MetaSetupPanel;
import games.strategy.engine.framework.startup.ui.SetupPanel;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorPanel;
import games.strategy.engine.framework.ui.BackBar;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import org.triplea.game.chat.ChatModel;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.SwingAction;
import org.triplea.swing.SwingComponents;
import org.triplea.swing.jpanel.GridBagConstraintsAnchor;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;
import org.triplea.swing.jpanel.JPanelBuilder;

/**
 * When the game launches, the MainFrame is loaded which will contain the MainPanel. The contents of
 * the MainPanel are swapped out until a new game has been started (TODO: check if the lobby uses
 * MainPanel at all).
 */
public class MainPanel extends JPanel {
  private static final long serialVersionUID = -5548760379892913464L;
  private static final Dimension initialSize = new Dimension(1000, 760);

  private final JButton playButton =
      new JButtonBuilder()
          .title(I18nEngineFramework.get().getText("startup.MainPanel.btn.Play.Lbl"))
          .toolTip(
              HtmlUtils.getHtml()
                  .addText(
                      I18nEngineFramework.get().getText("startup.MainPanel.btn.Play.Tltp.line1"))
                  .lineBreak()
                  .addText(
                      I18nEngineFramework.get().getText("startup.MainPanel.btn.Play.Tltp.line2"))
                  .toString())
          .build();
  // leaves a setup screen for the main menu, by the link back of its bar
  private final Runnable cancelAction;
  // the bar of a setup screen across the top, with the link back and the title
  private final JPanel barHolder = new JPanelBuilder().borderLayout().build();

  private final JPanel gameSetupPanelHolder = new JPanelBuilder().borderLayout().build();
  private final JScrollPane gameSetupPanelScroll = new JScrollPane(gameSetupPanelHolder);
  // the scroll pane for setup screens, or the main menu and the lobby, which fit without one
  private final JPanel setupArea = new JPanelBuilder().borderLayout().build();
  private final JPanel mainPanel;
  private final JSplitPane chatSplit;
  private final JPanel chatPanelHolder = new JPanelBuilder().height(62).borderLayout().build();
  // the buttons of the current screen, right of the quit button: [Network...] [Start Game]
  private final JPanel screenButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
  private final JPanel footer;
  private SetupPanel gameSetupPanel;
  private final GameSelectorPanel gameSelectorPanel;

  /**
   * MainPanel is the full contents of the 'mainFrame'. This panel represents the welcome screen and
   * subsequent screens.
   */
  MainPanel(
      final Runnable quitAction,
      final GameSelectorPanel gameSelectorPanel,
      final Consumer<MainPanel> launchAction,
      @Nullable final ChatModel chatModel,
      final Runnable cancelAction) {
    this.gameSelectorPanel = gameSelectorPanel;
    this.cancelAction = cancelAction;
    playButton.addActionListener(e -> launchAction.accept(this));
    LookAndFeel.setPrimaryButtonStyle(playButton);

    gameSelectorPanel.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, LookAndFeel.getSeparatorColor()),
            BorderFactory.createEmptyBorder(16, 16, 12, 16)));
    gameSetupPanelScroll.setBorder(BorderFactory.createEmptyBorder());
    SwingComponents.setScrollSpeed(gameSetupPanelScroll);
    chatSplit = new JSplitPane();
    chatSplit.setOrientation(JSplitPane.VERTICAL_SPLIT);
    chatSplit.setResizeWeight(0.8);
    chatSplit.setOneTouchExpandable(false);
    chatSplit.setDividerSize(5);

    mainPanel = new JPanelBuilder().border(0).gridBagLayout().build();
    mainPanel.add(
        gameSelectorPanel,
        new GridBagConstraintsBuilder(0, 0)
            .anchor(GridBagConstraintsAnchor.WEST)
            .fill(GridBagConstraintsFill.VERTICAL)
            .weightY(1.0)
            .build());

    mainPanel.add(
        setupArea,
        new GridBagConstraintsBuilder(1, 0)
            .anchor(GridBagConstraintsAnchor.CENTER)
            .fill(GridBagConstraintsFill.BOTH)
            .weightX(1.0)
            .weightY(1.0)
            .build());
    setLayout(new BorderLayout());

    if (chatModel instanceof ChatPanel) {
      addChat((ChatPanel) chatModel);
    } else {
      add(mainPanel, BorderLayout.CENTER);
    }

    final JButton quitButton =
        new JButtonBuilder()
            .title(I18nEngineFramework.get().getText("startup.MainPanel.btn.Quit.Lbl"))
            .toolTip(I18nEngineFramework.get().getText("startup.MainPanel.btn.Quit.Tltp"))
            .actionListener(quitAction)
            .build();
    footer =
        new JPanelBuilder()
            .border(LookAndFeel.newFooterBorder())
            .borderLayout()
            .addWest(quitButton)
            .addEast(screenButtons)
            .build();
    add(footer, BorderLayout.SOUTH);
    add(barHolder, BorderLayout.NORTH);
    setPreferredSize(initialSize);
    updatePlayButtonState();
  }

  private void addChat(final Component chatComponent) {
    remove(mainPanel);
    remove(chatSplit);
    chatPanelHolder.removeAll();

    chatPanelHolder.add(chatComponent, BorderLayout.CENTER);
    chatSplit.setTopComponent(mainPanel);
    chatSplit.setBottomComponent(chatPanelHolder);
    add(chatSplit, BorderLayout.CENTER);
  }

  /** This method will 'change' screens, swapping out one setup panel for another. */
  public void setSetupPanel(final SetupPanel panel) {
    if (gameSetupPanel != null) {
      gameSetupPanel.setPanelChangedListener(null);
    }
    gameSetupPanel = panel;
    // Note: The listener on the panel itself only updates widgets and doesn't
    // rebuild the UI components (this method), as that would cause focus to be lost.
    panel.setPanelChangedListener(setupPanel -> updatePlayButtonState());
    updatePlayButtonState();

    gameSetupPanelHolder.removeAll();
    setupArea.removeAll();
    // The main menu (choosing how to play) and the lobby fill the window: they have no game,
    // nothing to start, and the lobby has a bar of its own. The main menu fits the window without
    // scroll bars by hiding what does not fit; the lobby scrolls in a very low window.
    if (panel.isMainMenu()) {
      setupArea.add(panel, BorderLayout.CENTER);
    } else {
      gameSetupPanelHolder.add(panel, BorderLayout.CENTER);
      setupArea.add(gameSetupPanelScroll, BorderLayout.CENTER);
    }
    gameSelectorPanel.setVisible(!panel.isFullWindow());
    footer.setVisible(!panel.isFullWindow());
    barHolder.removeAll();
    barHolder.setVisible(!panel.isFullWindow());
    if (!panel.isFullWindow()) {
      // the way back is the link of the bar; the footer keeps the ways forward
      barHolder.add(
          new BackBar(MetaSetupPanel.getMainMenuTitle(), panel.getScreenTitle(), cancelAction),
          BorderLayout.CENTER);
    }
    screenButtons.removeAll();
    if (panel.isCancelButtonVisible()) {
      final List<Action> actions = gameSetupPanel.getUserActions();
      if (!actions.isEmpty()) {
        screenButtons.add(newUserActionMenuButton(actions));
      }
      screenButtons.add(playButton);
    }

    Optional.ofNullable(panel.getChatModel())
        .filter(ChatPanel.class::isInstance)
        .map(ChatPanel.class::cast)
        .ifPresentOrElse(
            this::addChat,
            () -> {
              remove(chatSplit);
              add(mainPanel);
            });

    revalidate();
    repaint();
  }

  private static JButton newUserActionMenuButton(final List<Action> actions) {
    // if we need this for something other than network, add a way to set it
    final JButton button =
        new JButton(I18nEngineFramework.get().getText("startup.MainPanel.btn.Network.Lbl"));
    button.addActionListener(
        e -> {
          final JPopupMenu menu = new JPopupMenu();
          for (final Action a : actions) {
            menu.add(a);
          }
          menu.show(button, 0, button.getHeight());
        });
    return button;
  }

  /** The title of the screen shown, eg "Main menu", where the link back of a page leads. */
  public String getScreenTitle() {
    return Optional.ofNullable(gameSetupPanel)
        .map(SetupPanel::getScreenTitle)
        .orElseGet(MetaSetupPanel::getMainMenuTitle);
  }

  public void updatePlayButtonState() {
    SwingAction.invokeNowOrLater(
        () -> playButton.setEnabled(gameSetupPanel != null && gameSetupPanel.canGameStart()));
  }
}
