/**
 * The Model of MVC (NFR-20): the game rules and data. Nothing here uses Swing, so every rule can be tested with
 * JUnit on its own. Small types live inside the class they belong to (for example {@code Game.Phase} or
 * {@code Move.Kind}). Main groups of classes:
 * <ul>
 *     <li>settings – {@link model.GameConfig} (with the settings of each level and the data folder),
 *         {@link model.Level};</li>
 *     <li>board – {@link model.Board}, {@link model.Square}, {@link model.SquareType}, {@link model.Seat},
 *         {@link model.Board.Layout} and {@link model.Board.EasyLayout}, {@link model.SquareFactory} (Factory
 *         pattern), {@link model.SpecialSquarePlacer};</li>
 *     <li>dice – {@link model.DiceRoll} and the {@link model.DiceStrategy} of each level (Strategy pattern);</li>
 *     <li>play – {@link model.Game} (the turn referee), {@link model.MoveRules} (legal moves, blocks, captures),
 *         {@link model.Move}, {@link model.Player}, {@link model.Piece}, {@link model.TurnTimer}, and
 *         {@link model.GameListener} through which screens follow the game (Observer pattern);</li>
 *     <li>setup – {@link model.SetupValidator}, {@link model.PieceColor};</li>
 *     <li>history – {@link model.GameHistory}, the CSV file of finished games;</li>
 *     <li>admin – {@link model.AdminAuthService}, {@link model.PasswordHasher}, {@link model.LoginThrottle},
 *         {@link model.Session}, and the admin files {@link model.AdminAccountStore},
 *         {@link model.FileAdminAccountStore}.</li>
 * </ul>
 */
package model;
