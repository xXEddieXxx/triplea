package org.triplea.game.client.ui.swing.laf;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.util.Collection;
import java.util.List;
import javax.swing.UIManager.LookAndFeelInfo;

/** Look-and-feel manager providing FlatLaf themes as available options. */
public final class DefaultSubstanceLookAndFeelManager implements SubstanceLookAndFeelManager {
  @Override
  public String getDefaultLookAndFeelClassName() {
    return TripleaDarkLaf.class.getName();
  }

  @Override
  public Collection<LookAndFeelInfo> getInstalledLookAndFeels() {
    return List.of(
        new LookAndFeelInfo(TripleaDarkLaf.NAME, TripleaDarkLaf.class.getName()),
        new LookAndFeelInfo(TripleaLightLaf.NAME, TripleaLightLaf.class.getName()),
        new LookAndFeelInfo("FlatLaf Light", FlatLightLaf.class.getName()),
        new LookAndFeelInfo("FlatLaf Dark", FlatDarkLaf.class.getName()),
        new LookAndFeelInfo("FlatLaf IntelliJ", FlatIntelliJLaf.class.getName()),
        new LookAndFeelInfo("FlatLaf Darcula", FlatDarculaLaf.class.getName()));
  }

  @Override
  public void initialize() {
    // FlatLaf.properties in this package: shapes and the "primary" button style for all themes
    FlatLaf.registerCustomDefaultsSource(DefaultSubstanceLookAndFeelManager.class.getPackageName());
  }
}
