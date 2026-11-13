/**
 * The View of MVC (NFR-20): the screens. They show the model and pass clicks on to the controllers, but never
 * decide what is legal. Main groups of classes:
 * <ul>
 *     <li>window and menus – {@link view.MainFrame}, {@link view.WelcomePanel}, {@link view.OpeningPanel},
 *         {@link view.SetupPanel}, {@link view.SettingsPanel}, {@link view.GameOverPanel};</li>
 *     <li>game screen – {@link view.GamePanel}, {@link view.PlayerCard}, {@link view.PlayerRow},
 *         {@link view.DicePanel}, and all its texts in {@link view.GameMessages};</li>
 *     <li>board – {@link view.BoardPanel}, which draws the board, the pieces, their moves and effects, and scales
 *         with the window;</li>
 *     <li>rules window – {@link view.RulesDialog} (texts, pictures and the square legend);</li>
 *     <li>admin screens – {@link view.AdminLoginPanel}, {@link view.AdminHomePanel};</li>
 *     <li>look – {@link view.UiTheme} (dark and light colors), {@link view.PantherPainter} (the 3D panthers),
 *         {@link view.Glow}, {@link view.Animator}, and {@link view.Neon} (the shared buttons, text fields,
 *         titles, background and cards).</li>
 * </ul>
 */
package view;
