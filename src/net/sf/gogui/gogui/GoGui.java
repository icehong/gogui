//----------------------------------------------------------------------------
// $Id$
//----------------------------------------------------------------------------

package net.sf.gogui.gogui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import net.sf.gogui.game.ConstClock;
import net.sf.gogui.game.ConstGame;
import net.sf.gogui.game.ConstGameInformation;
import net.sf.gogui.game.ConstGameTree;
import net.sf.gogui.game.ConstNode;
import net.sf.gogui.game.Game;
import net.sf.gogui.game.GameTree;
import net.sf.gogui.game.GameInformation;
import net.sf.gogui.game.MarkType;
import net.sf.gogui.game.NodeUtil;
import net.sf.gogui.game.TimeSettings;
import net.sf.gogui.go.BoardUtil;
import net.sf.gogui.go.ConstBoard;
import net.sf.gogui.go.CountScore;
import net.sf.gogui.go.GoColor;
import net.sf.gogui.go.GoPoint;
import net.sf.gogui.go.Komi;
import net.sf.gogui.go.Move;
import net.sf.gogui.gtp.GtpClient;
import net.sf.gogui.gtp.GtpClientUtil;
import net.sf.gogui.gtp.GtpError;
import net.sf.gogui.gtp.GtpSynchronizer;
import net.sf.gogui.gtp.GtpUtil;
import net.sf.gogui.gui.AnalyzeCommand;
import net.sf.gogui.gui.AnalyzeDialog;
import net.sf.gogui.gui.AnalyzeShow;
import net.sf.gogui.gui.BoardSizeDialog;
import net.sf.gogui.gui.Bookmark;
import net.sf.gogui.gui.BookmarkDialog;
import net.sf.gogui.gui.Comment;
import net.sf.gogui.gui.ConstGuiBoard;
import net.sf.gogui.gui.ContextMenu;
import net.sf.gogui.gui.EditBookmarksDialog;
import net.sf.gogui.gui.FindDialog;
import net.sf.gogui.gui.GameInfo;
import net.sf.gogui.gui.GameInfoDialog;
import net.sf.gogui.gui.GameTreePanel;
import net.sf.gogui.gui.GameTreeViewer;
import net.sf.gogui.gui.GtpShell;
import net.sf.gogui.gui.GuiBoard;
import net.sf.gogui.gui.GuiBoardUtil;
import net.sf.gogui.gui.GuiGtpClient;
import net.sf.gogui.gui.GuiGtpUtil;
import net.sf.gogui.gui.GuiUtil;
import net.sf.gogui.gui.Help;
import net.sf.gogui.gui.LiveGfx;
import net.sf.gogui.gui.OptionalMessage;
import net.sf.gogui.gui.ParameterDialog;
import net.sf.gogui.gui.RecentFileMenu;
import net.sf.gogui.gui.SelectProgram;
import net.sf.gogui.gui.Session;
import net.sf.gogui.gui.ScoreDialog;
import net.sf.gogui.gui.SimpleDialogs;
import net.sf.gogui.gui.StatusBar;
import net.sf.gogui.sgf.SgfReader;
import net.sf.gogui.sgf.SgfWriter;
import net.sf.gogui.tex.TexWriter;
import net.sf.gogui.text.TextParser;
import net.sf.gogui.text.ParseError;
import net.sf.gogui.thumbnail.ThumbnailCreator;
import net.sf.gogui.thumbnail.ThumbnailPlatform;
import net.sf.gogui.util.ErrorMessage;
import net.sf.gogui.util.FileUtil;
import net.sf.gogui.util.Platform;
import net.sf.gogui.util.ProgressShow;
import net.sf.gogui.version.Version;

/** Graphical user interface to a Go program. */
public class GoGui
    extends JFrame
    implements ActionListener, AnalyzeDialog.Callback,
               GuiBoard.Listener, GameTreeViewer.Listener, GtpShell.Listener,
               ScoreDialog.Listener, GoGuiMenuBar.BookmarkListener
{
    public GoGui(String program, File file, int move, String time,
                 boolean verbose, boolean computerBlack,
                 boolean computerWhite, boolean auto, String gtpFile,
                 String gtpCommand, String initAnalyze)
        throws GtpError, ErrorMessage
    {
        int boardSize = m_prefs.getInt("boardsize", GoPoint.DEFAULT_SIZE);
        m_beepAfterMove = m_prefs.getBoolean("beep-after-move", true);
        m_file = file;
        m_gtpFile = gtpFile;
        m_gtpCommand = gtpCommand;
        m_move = move;
        m_computerBlack = computerBlack;
        m_computerWhite = computerWhite;
        m_auto = auto;
        m_verbose = verbose;
        m_initAnalyze = initAnalyze;
        m_showInfoPanel = true;
        m_showToolbar = false;

        Container contentPane = getContentPane();        
        m_innerPanel = new JPanel(new BorderLayout());
        contentPane.add(m_innerPanel, BorderLayout.CENTER);
        m_toolBar = new GoGuiToolBar(this);

        m_infoPanel = new JPanel(new BorderLayout());
        m_game = new Game(boardSize);
        m_gameInfo = new GameInfo(m_game);
        m_gameInfo.setBorder(GuiUtil.createSmallEmptyBorder());
        m_infoPanel.add(m_gameInfo, BorderLayout.NORTH);
        m_guiBoard = new GuiBoard(boardSize);
        m_guiBoard.setListener(this);
        m_statusBar = new StatusBar(true);
        m_innerPanel.add(m_statusBar, BorderLayout.SOUTH);

        Comment.Listener commentListener = new Comment.Listener()
            {
                public void changed(String comment)
                {
                    if (m_gameTreeViewer != null)
                        m_gameTreeViewer.redrawCurrentNode();
                    m_game.setComment(comment);
                    updateModified();
                }

                public void textSelected(String text)
                {
                    if (text == null)
                        text = "";
                    GoPoint list[] =
                        GtpUtil.parsePointString(text, getBoardSize());
                    GuiBoardUtil.showPointList(m_guiBoard, list);
                }
            };
        m_comment = new Comment(commentListener);
        boolean monoFont = m_prefs.getBoolean("comment-font-fixed", false);
        m_comment.setMonoFont(monoFont);
        m_infoPanel.add(m_comment, BorderLayout.CENTER);
        m_splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                     m_guiBoard, m_infoPanel);
        GuiUtil.removeKeyBinding(m_splitPane, "F8");
        m_splitPane.setResizeWeight(1);
        m_innerPanel.add(m_splitPane, BorderLayout.CENTER);
        WindowAdapter windowAdapter = new WindowAdapter()
            {
                public void windowClosing(WindowEvent event)
                {
                    close();
                }
            };
        addWindowListener(windowAdapter);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        GuiUtil.setGoIcon(this);
        RecentFileMenu.Callback recentCallback = new RecentFileMenu.Callback()
            {
                public void fileSelected(String label, File file)
                {
                    if (! checkSaveGame())
                        return;
                    loadFile(file, -1);
                    boardChangedBegin(false, true);
                }
            };
        RecentFileMenu.Callback recentGtp = new RecentFileMenu.Callback()
            {
                public void fileSelected(String label, File file)
                {
                    if (m_gtpShell == null)
                        return;
                    sendGtpFile(file);
                    m_menuBar.addRecentGtp(file);
                }
            };
        m_menuBar = new GoGuiMenuBar(this, m_actions, recentCallback,
                                     recentGtp, this);
        m_treeLabels = m_prefs.getInt("gametree-labels",
                                      GameTreePanel.LABEL_NUMBER);
        m_treeSize = m_prefs.getInt("gametree-size",
                                    GameTreePanel.SIZE_NORMAL);
        boolean showSubtreeSizes =
            m_prefs.getBoolean("gametree-show-subtree-sizes", false);
        m_menuBar.setShowSubtreeSizes(showSubtreeSizes);
        m_menuBar.setAutoNumber(m_prefs.getBoolean("gtpshell-autonumber",
                                                   false));
        // JComboBox has problems on the Mac, see section Bugs in
        // documentation
        boolean completion
            = ! m_prefs.getBoolean("gtpshell-disable-completions",
                                Platform.isMac());
        m_menuBar.setCommandCompletion(completion);
        m_menuBar.setTimeStamp(m_prefs.getBoolean("gtpshell-timestamp",
                                                  false));
        m_showLastMove = m_prefs.getBoolean("show-last-move", true);        
        m_showVariations = m_prefs.getBoolean("show-variations", false);
        boolean showCursor = m_prefs.getBoolean("show-cursor", false);
        boolean showGrid = m_prefs.getBoolean("show-grid", true);
        m_guiBoard.setShowCursor(showCursor);
        m_guiBoard.setShowGrid(showGrid);
        setJMenuBar(m_menuBar.getMenuBar());
        if (program == null)
            m_program = m_prefs.get("program", null);
        else
            m_program = program;
        if (m_program != null && m_program.trim().equals(""))
            m_program = null;
        m_menuBar.setNormalMode();
        m_guiBoard.requestFocusInWindow();
        if (time != null)
            m_timeSettings = TimeSettings.parse(time);
        Runnable callback = new Runnable()
            {
                public void run()
                {
                    initialize();
                }
            };
        SwingUtilities.invokeLater(callback);
    }
    
    public void actionPerformed(ActionEvent event)
    {
        String command = event.getActionCommand();
        if (command.equals("auto-number"))
            cbAutoNumber();
        else if (command.equals("command-completion"))
            cbCommandCompletion();
        else if (command.equals("gametree-show-subtree-sizes"))
            cbGameTreeShowSubtreeSizes();
        else if (command.equals("timestamp"))
            cbTimeStamp();
        else
            assert(false);
    }
    
    public void actionAbout()
    {
        String protocolVersion = null;
        String command = null;
        if (m_gtp != null)
            command = m_gtp.getProgramCommand();
        AboutDialog.show(this, m_name, m_version, command);
    }

    public void actionAddBookmark()
    {
        if (m_file == null)
        {
            showError("Cannot set bookmark if no file loaded");
            return;
        }
        if (isModified())
        {
            showError("Cannot set bookmark if file modified");
            return;
        }
        if (getCurrentNode().getFatherConst() != null
            && getCurrentNode().getMove() == null)
        {
            showError("Cannot set bookmark at non-root node without move");
            return;
        }
        String variation = NodeUtil.getVariationString(getCurrentNode());
        int move = NodeUtil.getMoveNumber(getCurrentNode());
        Bookmark bookmark = new Bookmark(m_file, move, variation);
        if (! BookmarkDialog.show(this, "Add Bookmark", bookmark, true))
            return;
        m_bookmarks.add(bookmark);
        m_menuBar.setBookmarks(m_bookmarks);
    }

    public void actionAttachProgram()
    {        
        if (! checkCommandInProgress())
            return;
        String program = SelectProgram.select(this);
        if (program == null)
            return;
        if (m_gtp != null)
            if (! actionDetachProgram())
                return;
        if (! attachProgram(program))
        {
            m_prefs.put("program", "");
            updateViews();
            return;
        }
        m_prefs.put("program", m_program);
        if (m_gtpShell != null && m_session.isVisible("shell"))
            actionToggleShowShell();
        if (m_session.isVisible("analyze"))
            actionToggleShowAnalyzeDialog();
        toFrontLater();
        updateViews();
    }

    public void actionBackToMainVariation()
    {
        if (! checkSpecialMode())
            return;
        ConstNode node = NodeUtil.getBackToMainVariation(getCurrentNode());
        actionGotoNode(node);
    }

    public void actionBackward(int n)
    {
        if (! checkSpecialMode())
            return;
        backward(n);
        boardChangedBegin(false, false);
    }

    public void actionBeginning()
    {
        if (! checkSpecialMode())
            return;
        backward(NodeUtil.getDepth(getCurrentNode()));
        boardChangedBegin(false, false);
    }

    public void actionBoardSize(int boardSize)
    {
        if (! checkCommandInProgress())
            return;
        saveSession();
        newGame(boardSize, false);
        m_gameInfo.updateTimeFromClock(getClock());
        m_prefs.putInt("boardsize", boardSize);
    }

    public void actionBoardSizeOther()
    {
        if (! checkCommandInProgress())
            return;
        int size = BoardSizeDialog.show(this, getBoardSize());
        if (size < 1 || size > GoPoint.MAXSIZE)
            return;
        saveSession();
        newGame(size, false);
        m_prefs.putInt("boardsize", size);
        m_gameInfo.updateTimeFromClock(getClock());
    }
    
    public void actionClockHalt()
    {
        if (! getClock().isRunning())
            return;
        m_game.haltClock();
        updateViews();
    }

    public void actionClockResume()
    {
        if (getClock().isRunning())
            return;
        m_game.startClock();
        updateViews();
    }

    public void actionClockRestore()
    {        
        if (! NodeUtil.canRestoreTime(getCurrentNode(), getClock()))
            return;
        m_game.restoreClock();
        m_gameInfo.updateTimeFromClock(getClock());
        updateViews();
    }

    public void actionComputerColor(boolean isBlack, boolean isWhite)
    {
        m_computerBlack = isBlack;
        m_computerWhite = isWhite;
        if (! isCommandInProgress())
            checkComputerMove();
    }

    public void actionDeleteSideVariations()
    {
        if (! checkSpecialMode())
            return;
        if (! NodeUtil.isInMainVariation(getCurrentNode()))
            return;
        if (! showQuestion("Delete all variations but main?"))
            return;
        m_game.keepOnlyMainVariation();
        updateModified();
        boardChangedBegin(false, true);
    }

    public boolean actionDetachProgram()
    {        
        if (m_gtp == null)
            return false;
        if (isCommandInProgress() && ! showQuestion("Kill program?"))
            return false;
        detachProgram();
        m_prefs.put("program", "");
        updateViews();
        return true;
    }

    public void actionDocumentation()
    {
        ClassLoader classLoader = getClass().getClassLoader();
        URL url = classLoader.getResource("net/sf/gogui/doc/index.html");
        if (url == null)
        {
            showError("Help not found");
            return;
        }
        if (m_help == null)
        {
            m_help = new Help(this, url);
            restoreSize(m_help, "help");
        }
        m_help.setVisible(true);
        m_help.toFront();
    }

    public void actionEditBookmarks()
    {
        if (! EditBookmarksDialog.show(this, m_bookmarks))
            return;
        m_menuBar.setBookmarks(m_bookmarks);
    }

    public void actionEnd()
    {
        if (! checkSpecialMode())
            return;
        forward(NodeUtil.getNodesLeft(getCurrentNode()));
        boardChangedBegin(false, false);
    }

    public void actionExportLatexMainVariation()
    {
        File file = SimpleDialogs.showSave(this, "Export LaTeX");
        if (file == null)
            return;
        try
        {
            OutputStream out = new FileOutputStream(file);
            String title = FileUtil.removeExtension(new File(file.getName()),
                                                     "tex");
            new TexWriter(title, out, getTree(), false);
        }
        catch (FileNotFoundException e)
        {
            showError("Export failed", e);
        }
    }

    public void actionExportLatexPosition()
    {
        File file = SimpleDialogs.showSave(this, "Export LaTeX Position");
        if (file == null)
            return;
        try
        {
            OutputStream out = new FileOutputStream(file);
            String title = FileUtil.removeExtension(new File(file.getName()),
                                                     "tex");
            new TexWriter(title, out, getBoard(), false,
                          GuiBoardUtil.getLabels(m_guiBoard),
                          GuiBoardUtil.getMarkSquare(m_guiBoard),
                          GuiBoardUtil.getSelects(m_guiBoard));
        }
        catch (FileNotFoundException e)
        {
            showError("Export failed", e);
        }
    }

    public void actionExportSgfPosition()
    {
        File file = SimpleDialogs.showSaveSgf(this);
        if (file == null)
            return;
        try
        {
            savePosition(file);
        }
        catch (FileNotFoundException e)
        {
            showError("Could not save position", e);
        }
    }

    public void actionExportTextPosition()
    {
        File file = SimpleDialogs.showSave(this, "Export Text Position");
        if (file == null)
            return;
        try
        {
            String text = BoardUtil.toString(getBoard(), false);
            PrintStream out = new PrintStream(new FileOutputStream(file));
            out.print(text);
            out.close();
        }
        catch (FileNotFoundException e)
        {
            showError("Export failed", e);
        }
    }

    public void actionExportTextPositionToClipboard()
    {
        GuiUtil.copyToClipboard(BoardUtil.toString(getBoard(), false));
    }

    public void actionFind()
    {
        if (! checkSpecialMode())
            return;
        Pattern pattern = FindDialog.run(this, m_comment.getSelectedText());
        if (pattern == null)
            return;
        m_pattern = pattern;
        if (NodeUtil.commentContains(getCurrentNode(), m_pattern))
            m_comment.markAll(m_pattern);
        else
            actionFindNext();
        updateViews();
    }

    public void actionFindNext()
    {
        if (! checkSpecialMode())
            return;
        if (m_pattern == null)
            return;
        ConstNode root = getTree().getRootConst();
        ConstNode node = NodeUtil.findInComments(getCurrentNode(), m_pattern);
        if (node == null)
            if (getCurrentNode() != root)
                if (showQuestion("End of tree reached. Continue from start?"))
                {
                    node = root;
                    if (! NodeUtil.commentContains(node, m_pattern))
                        node = NodeUtil.findInComments(node, m_pattern);
                }
        if (node == null)
        {
            showInfo("Not found");
            m_pattern = null;
        }
        else
        {
            actionGotoNode(node);
            m_comment.markAll(m_pattern);
        }
    }

    public void actionForward(int n)
    {
        if (! checkSpecialMode())
            return;
        forward(n);
        boardChangedBegin(false, false);
    }

    public void actionGameInfo()
    {
        if (! checkCommandInProgress())
            // Changes in game info may send GTP commands
            return;
        GameInformation info = new GameInformation(getGameInformation());
        if (! GameInfoDialog.show(this, info))
            return;
        m_game.setDate(info.getDate());
        m_game.setPlayerBlack(info.getPlayerBlack());
        m_game.setPlayerWhite(info.getPlayerWhite());
        m_game.setResult(info.getResult());
        m_game.setRankBlack(info.getRankBlack());
        m_game.setRankWhite(info.getRankWhite());
        Komi prefsKomi = getPrefsKomi();
        Komi komi = info.getKomi();
        if (komi != null && ! komi.equals(prefsKomi))
        {
            m_prefs.put("komi", komi.toString());
            setKomi(komi);
        }
        if (info.getRules() != null
            && ! info.getRules().equals(m_prefs.get("rules", "")))
        {
            m_prefs.put("rules", info.getRules());
            setRules();
        }
        m_timeSettings = info.getTimeSettings();
        setTitle();
        updateViews();
    }

    public void actionGoto()
    {
        if (! checkSpecialMode())
            return;
        ConstNode node = MoveNumberDialog.show(this, getCurrentNode());
        if (node == null)
            return;
        actionGotoNode(node);
    }

    public void actionGotoBookmark(int i)
    {
        if (! checkSpecialMode())
            return;
        if (! checkSaveGame())
            return;
        if (i < 0 || i >= m_bookmarks.size())
            return;
        Bookmark bookmark = (Bookmark)m_bookmarks.get(i);
        File file = bookmark.m_file;
        if (m_file == null || ! file.equals(m_file))
            if (! loadFile(file, -1))
                return;
        String variation = bookmark.m_variation;
        ConstNode node = getTree().getRootConst();
        if (! variation.equals(""))
        {
            node = NodeUtil.findByVariation(node, variation);
            if (node == null)
            {
                showError("Bookmark has invalid variation");
                return;
            }
        }
        node = NodeUtil.findByMoveNumber(node, bookmark.m_move);
        if (node == null)
        {
            showError("Bookmark has invalid move number");
            return;
        }
        gotoNode(node);
        boardChangedBegin(false, true);
    }

    public void actionGotoNode(ConstNode node)
    {
        gotoNode(node);
        boardChangedBegin(false, false);
    }

    public void actionGotoVariation()
    {
        if (! checkSpecialMode())
            return;
        ConstNode node = GotoVariationDialog.show(this, getTree(),
                                                  getCurrentNode());
        if (node == null)
            return;
        actionGotoNode(node);
    }

    public void actionHandicap(int handicap)
    {
        if (! checkCommandInProgress())
            return;
        m_handicap = handicap;
        if (isModified())
            showInfo("Handicap will take effect on next game.");
        else
        {
            m_computerBlack = false;
            m_computerWhite = false;
            newGame(getBoardSize());
        }
    }

    public void actionImportTextPosition()
    {
        if (! checkSpecialMode())
            return;
        File file = SimpleDialogs.showOpen(this, "Import Text Position");
        if (file == null)
            return;
        try
        {
            importTextPosition(new FileReader(file));
        }
        catch (FileNotFoundException e)
        {
            showError("File not found");
        }
    }

    public void actionImportTextPositionFromClipboard()
    {
        if (! checkSpecialMode())
            return;
        String text = GuiUtil.getClipboardText();
        if (text == null)
            showError("No text selection in clipboard");
        else
            importTextPosition(new StringReader(text));
    }

    public void actionInterrupt()
    {
        if (! isCommandInProgress())
        {
            showError("Computer is not thinking");
            return;
        }
        if (m_gtp == null || m_gtp.isProgramDead())
            return;
        if (Interrupt.run(this, m_gtp))
            showStatus("Interrupting...");
    }

    public void actionKeepOnlyPosition()
    {
        if (! checkSpecialMode())
            return;
        if (! showQuestion("Delete all moves?"))
            return;
        m_game.keepOnlyPosition();
        initGtp();
        updateModified();
        boardChangedBegin(false, true);
    }

    public void actionMakeMainVariation()
    {
        if (! checkSpecialMode())
            return;
        if (! showQuestion("Make current to main variation?"))
            return;
        m_game.makeMainVariation();
        updateModified();
        boardChangedBegin(false, true);
    }

    public void actionNewGame()
    {
        if (! checkSpecialMode())
            return;
        newGame(getBoardSize(), true);
    }

    public void actionNextEarlierVariation()
    {
        if (! checkSpecialMode())
            return;
        ConstNode node = NodeUtil.getNextEarlierVariation(getCurrentNode());
        if (node != null)
            actionGotoNode(node);
    }

    public void actionNextVariation()
    {
        if (! checkSpecialMode())
            return;
        ConstNode node = NodeUtil.getNextVariation(getCurrentNode());
        if (node != null)
            actionGotoNode(node);
    }

    public void actionOpen()
    {
        if (! checkSpecialMode())
            return;
        if (! checkSaveGame())
            return;
        File file = SimpleDialogs.showOpenSgf(this);
        if (file == null)
            return;
        m_menuBar.addRecent(file);
        loadFile(file, -1);
        boardChangedBegin(false, true);
    }

    public void actionPass()
    {
        if (! checkSpecialMode())
            return;
        if (m_passWarning == null)
            m_passWarning = new OptionalMessage(this);
        if (! m_passWarning.showQuestion("Really pass?"))
            return;
        humanMoved(Move.getPass(getToMove()));
    }

    public void actionPlay(boolean isSingleMove)
    {
        if (! checkSpecialMode())
            return;
        if (m_gtp == null || ! checkProgramInSync())
            return;
        if (! isSingleMove && ! isComputerBoth())
        {
            m_computerBlack = false;
            m_computerWhite = false;
            if (getToMove() == GoColor.BLACK)
                m_computerBlack = true;
            else
                m_computerWhite = true;
        }
        m_interruptComputerBoth = false;
        generateMove(isSingleMove);
        if (getCurrentNode() == getTree().getRootConst()
            && getCurrentNode().getNumberChildren() == 0)
            m_game.resetClock();
        m_game.startClock();
    }

    public void actionPreviousEarlierVariation()
    {
        if (! checkSpecialMode())
            return;
        ConstNode node =
            NodeUtil.getPreviousEarlierVariation(getCurrentNode());
        if (node != null)
            actionGotoNode(node);
    }

    public void actionPreviousVariation()
    {
        if (! checkSpecialMode())
            return;
        ConstNode node = NodeUtil.getPreviousVariation(getCurrentNode());
        if (node != null)
            actionGotoNode(node);
    }

    public void actionPrint()
    {
        Print.run(this, m_guiBoard);
    }

    public void actionSave()
    {
        if (m_file == null)
            saveDialog();
        else
        {
            if (m_file.exists())
            {
                if (m_overwriteWarning == null)
                    m_overwriteWarning = new OptionalMessage(this);
                String message = "Overwrite " + m_file + "?";
                if (! m_overwriteWarning.showWarning(message))
                    return;
            }
            save(m_file);
        }
    }

    public void actionSaveAs()
    {
        saveDialog();
    }

    public void actionScore()
    {
        if (m_scoreMode)
            return;
        if (! checkSpecialMode())
            return;
        if (m_gtp == null)
        {
            showInfo("No program is attached.\n" +
                     "Please mark dead groups manually.");
            initScore(null);
            return;
        }
        if (m_gtp.isCommandSupported("final_status_list"))
        {
            Runnable callback = new Runnable()
                {
                    public void run()
                    {
                        cbScoreContinue();
                    }
                };
            showStatus("Scoring...");
            runLengthyCommand("final_status_list dead", callback);
        }
        else
        {
            showInfo(m_name + " does not support scoring.\n" +
                     "Please mark dead groups manually.");
            initScore(null);
        }
    }

    public void actionScoreDone(boolean accepted)
    {
        if (! m_scoreMode)
            return;
        m_scoreDialog.setVisible(false);
        if (accepted)
        {
            Komi komi = getGameInformation().getKomi();
            setResult(m_countScore.getScore(komi, getRules()).formatResult());
        }
        clearStatus();
        m_guiBoard.clearAll();
        m_scoreMode = false;
        m_menuBar.setNormalMode();
    }

    public void actionSetup()
    {
        if (! checkCommandInProgress())
            return;
        if (m_setupMode)
        {
            setupDone();
            return;
        }        
        resetBoard();
        m_setupMode = true;
        showStatus("Setup Black");
        m_game.setToMove(GoColor.BLACK);
        if (getCurrentNode().getMove() != null)
        {
            m_game.createNewChild();
            currentNodeChanged();
            updateGameTree(true);
        }
    }

    public void actionSetupColor(GoColor color)
    {
        assert(color.isBlackWhite());
        if (color == GoColor.BLACK)            
            showStatus("Setup Black");
        else
            showStatus("Setup White");
        m_game.setToMove(color);
        updateViews();
    }

    public void actionShellSave()
    {
        if (m_gtpShell == null)
            return;
        m_gtpShell.saveLog(this);
    }

    public void actionShellSaveCommands()
    {
        if (m_gtpShell == null)
            return;
        m_gtpShell.saveCommands(this);
    }

    public void actionShellSendFile()
    {
        if (! checkSpecialMode())
            return;
        if (m_gtpShell == null)
            return;
        File file = SimpleDialogs.showOpen(this, "Choose GTP file");
        if (file == null)
            return;
        sendGtpFile(file);
        m_menuBar.addRecentGtp(file);
    }

    public void actionToggleBeepAfterMove()
    {
        m_beepAfterMove = ! m_beepAfterMove;
        m_prefs.putBoolean("beep-after-move", m_beepAfterMove);
    }

    public void actionToggleCommentMonoFont()
    {
        boolean monoFont = ! m_comment.getMonoFont();
        m_comment.setMonoFont(monoFont);
        m_prefs.putBoolean("comment-font-fixed", monoFont);
    }

    public void actionToggleShowAnalyzeDialog()
    {        
        if (m_gtp == null)
            return;
        if (! checkProgramInSync())
            return;
        if (m_analyzeDialog == null)
        {
            m_analyzeDialog =
                new AnalyzeDialog(this, this, m_gtp.getSupportedCommands(),
                                  m_programAnalyzeCommands, m_gtp);
            m_analyzeDialog.addWindowListener(new WindowAdapter()
                {
                    public void windowClosing(WindowEvent e)
                    {
                        updateViews();
                    }
                });
            m_analyzeDialog.setBoardSize(getBoardSize());
            restoreSize(m_analyzeDialog, "analyze");
            setTitle();
            m_analyzeDialog.setVisible(true);
        }
        else
        {
            if (m_analyzeDialog != null)
                m_analyzeDialog.close();
            m_analyzeDialog = null;
        }
    }

    public void actionToggleShowCursor()
    {
        boolean showCursor = ! m_guiBoard.getShowCursor();
        m_guiBoard.setShowCursor(showCursor);
        m_prefs.putBoolean("show-cursor", showCursor);
    }

    public void actionToggleShowGrid()
    {
        boolean showGrid = ! m_guiBoard.getShowGrid();
        m_guiBoard.setShowGrid(showGrid);
        m_prefs.putBoolean("show-grid", showGrid);
    }

    public void actionToggleShowInfoPanel()
    {
        if (GuiUtil.isNormalSizeMode(this))
        {
            if (m_showInfoPanel)
                m_comment.setPreferredSize(m_comment.getSize());
            m_guiBoard.setPreferredFieldSize(m_guiBoard.getFieldSize());
        }
        showInfoPanel(! m_showInfoPanel);
        updateViews();
    }

    public void actionToggleShowLastMove()
    {
        m_showLastMove = ! m_showLastMove;
        m_prefs.putBoolean("show-last-move", m_showLastMove);
        updateFromGoBoard();
        updateViews();
    }

    public void actionToggleShowShell()
    {
        if (m_gtpShell == null)
            return;
        m_gtpShell.setVisible(! m_gtpShell.isVisible());
    }

    public void actionToggleShowToolbar()
    {
        if (GuiUtil.isNormalSizeMode(this))
        {
            if (m_showInfoPanel)
                m_comment.setPreferredSize(m_comment.getSize());
            m_guiBoard.setPreferredFieldSize(m_guiBoard.getFieldSize());
        }
        showToolbar(! m_showToolbar);
        updateViews();
    }

    public void actionToggleShowTree()
    {
        if (m_gameTreeViewer == null)
        {
            m_gameTreeViewer = new GameTreeViewer(this, this);
            m_gameTreeViewer.setLabelMode(m_treeLabels);
            m_gameTreeViewer.setSizeMode(m_treeSize);
            boolean showSubtreeSizes = m_menuBar.getShowSubtreeSizes();
            m_gameTreeViewer.setShowSubtreeSizes(showSubtreeSizes);
            restoreSize(m_gameTreeViewer, "tree");
            updateGameTree(true);
            // updateGameTree can close viewer
            if (m_gameTreeViewer != null)
                m_gameTreeViewer.setVisible(true);
        }
        else
            disposeGameTree();
    }

    public void actionToggleShowVariations()
    {
        m_showVariations = ! m_showVariations;
        m_prefs.putBoolean("show-variations", m_showVariations);
        resetBoard();
        updateViews();
    }

    public void actionTreeLabels(int mode)
    {
        m_treeLabels = mode;
        m_prefs.putInt("gametree-labels", mode);
        if (m_gameTreeViewer != null)
        {
            m_gameTreeViewer.setLabelMode(mode);
            updateGameTree(true);
        }
        updateViews();
    }

    public void actionTreeSize(int mode)
    {
        m_treeSize = mode;
        m_prefs.putInt("gametree-size", mode);
        if (m_gameTreeViewer != null)
        {
            m_gameTreeViewer.setSizeMode(mode);
            updateGameTree(true);
        }
        updateViews();
    }

    public void actionTruncate()
    {
        if (! checkSpecialMode())
            return;
        if (getCurrentNode().getFatherConst() == null)
            return;
        if (! showQuestion("Truncate current?"))
            return;
        m_game.truncate();
        updateModified();
        boardChangedBegin(false, true);
    }

    public void actionTruncateChildren()
    {
        if (! checkSpecialMode())
            return;
        int numberChildren = getCurrentNode().getNumberChildren();
        if (numberChildren == 0)
            return;
        if (! showQuestion("Truncate children?"))
            return;
        m_game.truncateChildren();
        updateModified();
        boardChangedBegin(false, true);
    }

    public void actionQuit()
    {
        close();
    }

    public void cbAutoNumber(boolean enable)
    {
        if (m_gtp != null)
            m_gtp.setAutoNumber(enable);
    }

    public void cbCommandCompletion()
    {
        if (m_gtpShell == null)
            return;
        boolean commandCompletion = m_menuBar.getCommandCompletion();
        m_gtpShell.setCommandCompletion(commandCompletion);
        m_prefs.putBoolean("gtpshell-disable-completions",
                           ! commandCompletion);
    }

    public void clearAnalyzeCommand()
    {
        clearAnalyzeCommand(true);
    }

    public void clearAnalyzeCommand(boolean resetBoard)
    {
        if (m_analyzeCommand != null)
        {
            if (isCommandInProgress())
            {
                showError("Cannot clear analyze command\n" +
                          "while command in progress");
                return;
            }
            m_analyzeCommand = null;
            setBoardCursorDefault();
        }
        if (resetBoard)
        {
            resetBoard();
            clearStatus();
        }
    }

    public boolean getBeepAfterMove()
    {
        return m_beepAfterMove;
    }

    public boolean getCommentMonoFont()
    {
        return m_comment.getMonoFont();
    }

    public boolean getShowLastMove()
    {
        return m_showLastMove;
    }

    public boolean getShowVariations()
    {
        return m_showVariations;
    }

    public int getTreeLabels()
    {
        return m_treeLabels;
    }

    public int getTreeSize()
    {
        return m_treeSize;
    }

    public boolean isAnalyzeDialogShown()
    {
        return (m_analyzeDialog != null);
    }

    /** Check if computer plays a color (or both). */
    public boolean isComputerColor(GoColor color)
    {
        if (color == GoColor.BLACK)
            return m_computerBlack;
        assert(color == GoColor.WHITE);
        return m_computerWhite;
    }

    public boolean isInfoPanelShown()
    {
        return m_showInfoPanel;
    }

    public boolean isShellShown()
    {
        return (m_gtpShell != null && m_gtpShell.isVisible());
    }

    public boolean isToolbarShown()
    {
        return m_showToolbar;
    }

    public boolean isTreeShown()
    {
        return (m_gameTreeViewer != null);
    }

    public void contextMenu(GoPoint point, Component invoker, int x, int y)
    {
        if (isCommandInProgress())
            return;
        if (m_setupMode
            || (m_analyzeCommand != null
                && m_analyzeCommand.needsPointListArg()))
        {
            fieldClicked(point, true);
            return;
        }
        ContextMenu contextMenu = createContextMenu(point);
        contextMenu.show(invoker, x, y);
    }

    public void disposeGameTree()
    {
        if (m_gameTreeViewer == null)
            return;
        m_gameTreeViewer.dispose();
        m_gameTreeViewer = null;
        updateViews();
    }

    public void fieldClicked(GoPoint p, boolean modifiedSelect)
    {
        if (! checkCommandInProgress())
            return;
        if (m_setupMode)
        {
            GoColor toMove = getToMove();
            GoColor color;
            if (modifiedSelect)
                color = toMove.otherColor();
            else
                color = toMove;
            if (getBoard().getColor(p) == color)
                color = GoColor.EMPTY;
            setup(p, color);
            m_game.setToMove(toMove);
            updateGameInfo(true);
            updateFromGoBoard();
            updateModified();
        }
        else if (m_analyzeCommand != null && m_analyzeCommand.needsPointArg()
                 && ! modifiedSelect)
        {
            m_analyzeCommand.setPointArg(p);
            m_guiBoard.clearAllSelect();
            m_guiBoard.setSelect(p, true);
            analyzeBegin(false);
            return;
        }
        else if (m_analyzeCommand != null
                 && m_analyzeCommand.needsPointListArg())
        {
            ArrayList pointListArg = m_analyzeCommand.getPointListArg();
            if (pointListArg.contains(p))
            {
                pointListArg.remove(p);
                if (modifiedSelect)
                    pointListArg.add(p);
            }
            else
                pointListArg.add(p);
            m_guiBoard.clearAllSelect();
            GuiBoardUtil.setSelect(m_guiBoard, pointListArg, true);
            if (modifiedSelect && pointListArg.size() > 0)
                analyzeBegin(false);
            return;
        }
        else if (m_scoreMode && ! modifiedSelect)
        {
            GuiBoardUtil.scoreSetDead(m_guiBoard, m_countScore, getBoard(), p);
            Komi komi = getGameInformation().getKomi();
            m_scoreDialog.showScore(m_countScore.getScore(komi, getRules()));
            return;
        }
        else if (modifiedSelect)
            m_guiBoard.contextMenu(p);
        else
        {
            if (getBoard().isSuicide(p, getToMove())
                && ! showQuestion("Play suicide?"))
                return;
            else if (getBoard().isKo(p)
                && ! showQuestion("Play illegal Ko move?"))
                return;
            Move move = Move.get(p, getToMove());
            humanMoved(move);
        }
    }

    public GoGuiActions getActions()
    {
        return m_actions;
    }

    public File getFile()
    {
        return m_file;
    }

    public ConstGame getGame()
    {
        return m_game;
    }

    public ConstGuiBoard getGuiBoard()
    {
        return m_guiBoard;
    }

    public int getHandicapDefault()
    {
        return m_handicap;
    }

    public boolean getMonoFont()
    {
        return m_comment.getMonoFont();
    }

    /** Get currently used pattern for search in comments. */
    public Pattern getPattern()
    {
        return m_pattern;
    }

    public GoColor getSetupColor()
    {
        return m_game.getToMove();
    }

    public boolean isInSetupMode()
    {
        return m_setupMode;
    }

    public boolean sendGtpCommand(String command, boolean sync)
        throws GtpError
    {
        if (isCommandInProgress() || m_gtp == null)
            return false;
        if (! checkProgramInSync())
            return false;
        if (sync)
        {
            m_gtp.send(command);
            return true;
        }
        Runnable callback = new Runnable()
            {
                public void run()
                {
                    sendGtpCommandContinue();
                }
            };
        m_gtp.send(command, callback);
        beginLengthyCommand();
        return true;
    }

    public void sendGtpCommandContinue()
    {
        endLengthyCommand();
    }

    public void initAnalyzeCommand(AnalyzeCommand command, boolean autoRun,
                                   boolean clearBoard)
    {
        if (m_gtp == null)
            return;
        if (! checkProgramInSync())
            return;
        m_analyzeCommand = command;
        m_analyzeAutoRun = autoRun;
        m_analyzeClearBoard = clearBoard;
        if (command.needsPointArg())
        {
            setBoardCursor(Cursor.HAND_CURSOR);
            showStatusSelectTarget();
        }
        else if (command.needsPointListArg())
        {
            setBoardCursor(Cursor.HAND_CURSOR);
            showStatusSelectPointList();
        }
    }

    public boolean isModified()
    {
        return m_game.isModified();
    }

    public boolean isProgramAttached()
    {
        return (m_gtp != null);
    }

    public void setAnalyzeCommand(AnalyzeCommand command, boolean autoRun,
                                  boolean clearBoard, boolean oneRunOnly)
    {
        if (isCommandInProgress())
        {
            showError("Cannot run analyze command\n" +
                      "while command in progress");
            return;
        }
        if (! checkProgramInSync())
            return;
        initAnalyzeCommand(command, autoRun, clearBoard);
        m_analyzeOneRunOnly = oneRunOnly;
        boolean needsPointArg = m_analyzeCommand.needsPointArg();
        if (needsPointArg && ! m_analyzeCommand.isPointArgMissing())
        {
            m_guiBoard.clearAllSelect();
            m_guiBoard.setSelect(m_analyzeCommand.getPointArg(), true);
        }
        else if (needsPointArg || m_analyzeCommand.needsPointListArg())
        {
            m_guiBoard.clearAllSelect();
            if (m_analyzeCommand.getType() == AnalyzeCommand.EPLIST)
                GuiBoardUtil.setSelect(m_guiBoard,
                                        m_analyzeCommand.getPointListArg(),
                                        true);
            toFront();
            return;
        }
        analyzeBegin(false);
    }    

    private class AnalyzeContinue
        implements Runnable
    {
        public AnalyzeContinue(boolean checkComputerMove)
        {
            m_checkComputerMove = checkComputerMove;
        }

        public void run()
        {
            analyzeContinue(m_checkComputerMove);
        }
        
        private final boolean m_checkComputerMove;
    }

    private class ShowInvalidResponse
        implements Runnable
    {
        public ShowInvalidResponse(String line)
        {
            m_line = line;
        }

        public void run()
        {
            if (m_line.trim().equals(""))
                showWarning("Invalid empty response line");
            else
                showWarning("Invalid response:\n" + m_line);
        }
        
        private final String m_line;
    }

    private static class LoadFileRunnable
        implements GuiUtil.ProgressRunnable
    {
        LoadFileRunnable(FileInputStream in, File file)
        {
            m_in = in;
            m_file = file;            
        }

        public SgfReader getReader()
        {
            return m_reader;
        }

        public void run(ProgressShow progressShow) throws Throwable
        {
            m_reader = new SgfReader(m_in, m_file.toString(), progressShow,
                                     m_file.length());
        }

        private final File m_file;

        private final FileInputStream m_in;

        private SgfReader m_reader;
    }

    private boolean m_analyzeAutoRun;

    private boolean m_analyzeClearBoard;

    private boolean m_analyzeOneRunOnly;

    private final boolean m_auto;

    private boolean m_beepAfterMove;

    private boolean m_computerBlack;

    private boolean m_computerWhite;

    private boolean m_ignoreInvalidResponses;

    /** State variable used between cbInterrupt and computerMoved. */
    private boolean m_interruptComputerBoth;

    /** State variable used between generateMove and computerMoved. */
    private boolean m_isSingleMove;

    private boolean m_lostOnTimeShown;

    private boolean m_resigned;

    private boolean m_scoreMode;

    private boolean m_setupMode;

    private boolean m_showInfoPanel;

    private boolean m_showLastMove;

    private boolean m_showToolbar;

    private boolean m_showVariations;

    private final boolean m_verbose;

    private int m_handicap;

    private final int m_move;

    private int m_treeLabels;

    private int m_treeSize;

    /** Serial version to suppress compiler warning.
        Contains a marker comment for use with serialver.sourceforge.net
    */
    private static final long serialVersionUID = 0L; // SUID

    private final GuiBoard m_guiBoard;

    private GuiGtpClient m_gtp;

    private final Comment m_comment;

    /** File corresponding to the current game. */
    private File m_file;

    private final GameInfo m_gameInfo;    

    private GtpShell m_gtpShell;

    private GameTreeViewer m_gameTreeViewer;

    private Help m_help;

    private final JPanel m_infoPanel;

    private final JPanel m_innerPanel;

    private final JSplitPane m_splitPane;

    private final GoGuiMenuBar m_menuBar;

    private Game m_game;

    private OptionalMessage m_gameFinishedMessage;

    private OptionalMessage m_overwriteWarning;

    private OptionalMessage m_passWarning;

    private OptionalMessage m_saveQuestion;

    private Pattern m_pattern;

    private AnalyzeCommand m_analyzeCommand;

    private final Session m_session = new Session("");

    private final CountScore m_countScore = new CountScore();

    private final StatusBar m_statusBar;

    private final String m_gtpCommand;

    private final String m_gtpFile;

    private final String m_initAnalyze;

    private String m_lastAnalyzeCommand;

    private String m_name;

    private String m_program;

    private String m_titleFromProgram;

    private String m_version = "";

    private AnalyzeDialog m_analyzeDialog;    

    private final Preferences m_prefs =
        Preferences.userNodeForPackage(getClass());

    private ScoreDialog m_scoreDialog;

    private String m_programAnalyzeCommands;

    private final ThumbnailCreator m_thumbnailCreator =
        new ThumbnailCreator(false);

    private TimeSettings m_timeSettings;

    /** Timer to make progress bar visible with some delay.
        Avoids showing the progress bar for short thinking times.
    */
    private Timer m_progressBarTimer;

    private final GoGuiActions m_actions = new GoGuiActions(this);

    private final GoGuiToolBar m_toolBar;

    private ArrayList m_bookmarks;

    private void analyzeBegin(boolean checkComputerMove)
    {
        if (m_gtp == null || m_analyzeCommand == null
            || m_analyzeCommand.isPointArgMissing())
            return;
        showStatus(m_analyzeCommand.getResultTitle());
        GoColor toMove = getToMove();
        m_lastAnalyzeCommand = m_analyzeCommand.replaceWildCards(toMove);
        runLengthyCommand(m_lastAnalyzeCommand,
                          new AnalyzeContinue(checkComputerMove));
    }

    private void analyzeContinue(boolean checkComputerMove)
    {
        if (m_analyzeClearBoard)
            resetBoard();
        if (! endLengthyCommand())
            return;
        String title = m_analyzeCommand.getResultTitle();
        try
        {
            clearStatus();
            String response = m_gtp.getResponse();
            AnalyzeShow.show(m_analyzeCommand, m_guiBoard, m_statusBar,
                             getBoard(), response);
            int type = m_analyzeCommand.getType();
            GoPoint pointArg = null;
            if (m_analyzeCommand.needsPointArg())
                pointArg = m_analyzeCommand.getPointArg();
            else if (m_analyzeCommand.needsPointListArg())
            {
                ArrayList list = m_analyzeCommand.getPointListArg();
                if (list.size() > 0)
                    pointArg = (GoPoint)list.get(list.size() - 1);
            }
            if (type == AnalyzeCommand.PARAM)
                ParameterDialog.editParameters(m_lastAnalyzeCommand, this,
                                               title, response, m_gtp);
            if (AnalyzeCommand.isTextType(type))
            {
                if (response.indexOf("\n") < 0)
                {
                    if (response.trim().equals(""))
                        response = "(empty response)";
                    showStatus(title + ": " + response);
                }
                else
                    GoGuiUtil.showAnalyzeTextOutput(this, m_guiBoard, type,
                                                    pointArg, title, response);
            }
            if ("".equals(m_statusBar.getText()) &&
                type != AnalyzeCommand.PARAM && type != AnalyzeCommand.NONE)
                showStatus(title);
            if (checkComputerMove)
                checkComputerMove();
        }
        catch (GtpError e)
        {                
            showStatus(title);
            showError(e);
        }
        finally
        {
            if (m_analyzeOneRunOnly)
                clearAnalyzeCommand(false);
        }
    }

    /** Attach program.
        @return true if program was successfully attached.
    */
    private boolean attachProgram(String program)
    {
        program = program.trim();
        if (program.equals(""))
            return false;
        m_program = program;
        if (m_gtpShell != null)
        {
            m_gtpShell.dispose();
            m_gtpShell = null;
        }
        m_gtpShell = new GtpShell(this, this);
        m_gtpShell.addWindowListener(new WindowAdapter()
            {
                public void windowClosing(WindowEvent e)
                {
                    updateViews();
                }
            });
        m_gtpShell.setProgramCommand(program);
        m_gtpShell.setTimeStamp(m_menuBar.getTimeStamp());
        m_gtpShell.setCommandCompletion(m_menuBar.getCommandCompletion());
        m_ignoreInvalidResponses = false;
        GtpClient.InvalidResponseCallback invalidResponseCallback =
            new GtpClient.InvalidResponseCallback()
            {
                public void show(String line)
                {
                    if (m_ignoreInvalidResponses)
                        return;
                    m_ignoreInvalidResponses = true;
                    Runnable runnable = new ShowInvalidResponse(line);
                    if (SwingUtilities.isEventDispatchThread())
                        runnable.run();
                    else
                        invokeAndWait(runnable);
                }
            };
        GtpClient.IOCallback ioCallback = new GtpClient.IOCallback()
            {
                public void receivedInvalidResponse(String s)
                {
                    if (m_gtpShell != null)
                        m_gtpShell.receivedInvalidResponse(s);
                }

                public void receivedResponse(boolean error, String s)
                {
                    if (m_gtpShell != null)
                        m_gtpShell.receivedResponse(error, s);
                }

                public void receivedStdErr(String s)
                {
                    if (m_gtpShell != null)
                    {
                        m_gtpShell.receivedStdErr(s);
                        m_liveGfx.receivedStdErr(s);
                    }
                }

                public void sentCommand(String s)
                {
                    if (m_gtpShell != null)
                        m_gtpShell.sentCommand(s);
                }

                private LiveGfx m_liveGfx =
                    new LiveGfx(getBoard(), m_guiBoard, m_statusBar);
            };
        GtpSynchronizer.Callback synchronizerCallback =
            new GtpSynchronizer.Callback()
            {
                public void run(int moveNumber)
                {
                    String text = "[" + moveNumber + "]";
                    m_statusBar.immediatelyPaintMoveText(text);
                }
            };
        try
        {
            GtpClient gtp = new GtpClient(m_program, m_verbose, ioCallback);
            gtp.setInvalidResponseCallback(invalidResponseCallback);
            gtp.setAutoNumber(m_menuBar.getAutoNumber());
            m_gtp = new GuiGtpClient(gtp, this, synchronizerCallback);
            m_gtp.start();
        }
        catch (GtpError e)
        {
            showError(e);
            return false;
        }
        m_name = null;
        m_titleFromProgram = null;
        try
        {
            m_name = m_gtp.send("name").trim();
        }
        catch (GtpError e)
        {
        }
        if (m_name == null)
            m_name = "Unknown Program";
        try
        {
            m_gtp.queryProtocolVersion();
        }
        catch (GtpError e)
        {
        }
        try
        {
            m_version = m_gtp.queryVersion();
            m_gtpShell.setProgramVersion(m_version);
            m_gtp.querySupportedCommands();
            m_gtp.queryInterruptSupport();
        }
        catch (GtpError e)
        {
        }        
        m_programAnalyzeCommands = m_gtp.getAnalyzeCommands();
        restoreSize(m_gtpShell, "shell");
        m_gtpShell.setProgramName(m_name);
        ArrayList supportedCommands =
            m_gtp.getSupportedCommands();
        m_gtpShell.setInitialCompletions(supportedCommands);
        if (! m_gtpFile.equals(""))
            sendGtpFile(new File(m_gtpFile));
        if (! m_gtpCommand.equals(""))
            sendGtpString(m_gtpCommand);
        initGtp();
        setTitle();
        currentNodeChanged();
        return true;
    }    

    /** Go backward a number of nodes in the tree. */
    private void backward(int n)
    {
        m_game.backward(n);
        currentNodeChanged();
    }

    private void beginLengthyCommand()
    {
        m_progressBarTimer.restart();
        m_gtpShell.setCommandInProgess(true);
    }

    private void boardChangedBegin(boolean doCheckComputerMove,
                                   boolean gameTreeChanged)
    {
        updateFromGoBoard();
        updateGameInfo(gameTreeChanged);
        updateViews();
        if (m_gtp != null
            && ! isOutOfSync()
            && m_analyzeCommand != null
            && m_analyzeAutoRun
            && ! m_analyzeCommand.isPointArgMissing())
            analyzeBegin(doCheckComputerMove);
        else
        {
            resetBoard();
            clearStatus();
            if (doCheckComputerMove)
                checkComputerMove();
        }
    }

    private void cbAutoNumber()
    {
        if (m_gtp == null)
            return;
        boolean enable = m_menuBar.getAutoNumber();
        m_gtp.setAutoNumber(enable);
        m_prefs.putBoolean("gtpshell-autonumber", enable);
    }

    private void cbGameTreeShowSubtreeSizes()
    {
        boolean enable = m_menuBar.getShowSubtreeSizes();
        m_prefs.putBoolean("gametree-show-subtree-sizes", enable);
        if (m_gameTreeViewer != null)
        {
            m_gameTreeViewer.setShowSubtreeSizes(enable);
            updateGameTree(true);
        }
    }

    private void cbScoreContinue()
    {
        boolean success = endLengthyCommand();
        clearStatus();
        GoPoint[] isDeadStone = null;
        if (success)
        {
            String response = m_gtp.getResponse();
            try
            {
                isDeadStone = GtpUtil.parsePointList(response, getBoardSize());
            }
            catch (GtpError error)
            {
                showError(error);
            }
        }
        initScore(isDeadStone);
    }    

    private void cbTimeStamp()
    {
        if (m_gtpShell == null)
            return;
        boolean enable = m_menuBar.getTimeStamp();
        m_gtpShell.setTimeStamp(enable);
        m_prefs.putBoolean("gtpshell-timestamp", enable);
    }

    private boolean checkCommandInProgress()
    {
        if (isCommandInProgress())
        {
            showError("Cannot execute while computer is thinking");
            return false;
        }
        return true;
    }

    private void checkComputerMove()
    {
        if (m_gtp == null || isOutOfSync())
            return;
        int moveNumber = NodeUtil.getMoveNumber(getCurrentNode());
        boolean bothPassed = (moveNumber >= 2 && getBoard().bothPassed());
        boolean gameFinished = (bothPassed || m_resigned);
        if (isComputerBoth())
        {
            if (gameFinished)
            {
                if (m_auto)
                {
                    newGame(getBoardSize());
                    checkComputerMove();
                    return;
                }
                m_game.haltClock();
                showGameFinished();
                return;
            }
            generateMove(false);            
        }
        else
        {
            if (gameFinished)
            {
                m_game.haltClock();
                showGameFinished();
                return;
            }
            else if (computerToMove())
                generateMove(false);
        }
    }

    private void checkLostOnTime(GoColor color)
    {
        if (getClock().lostOnTime(color)
            && ! getClock().lostOnTime(color.otherColor())
            && ! m_lostOnTimeShown)
        {
            if (color == GoColor.BLACK)
            {
                showInfo("Black lost on time.");
                setResult("W+Time");
            }
            else
            {
                assert(color == GoColor.WHITE);
                showInfo("White lost on time.");
                setResult("B+Time");
            }
            m_lostOnTimeShown = true;
        }
    }

    private boolean checkProgramInSync()
    {
        if (isOutOfSync())
        {
            showError("Could not synchronize current\n" +
                      "position with Go program");
            return false;
        }
        return true;
    }

    /** Ask for saving file if it was modified.
        @return true If file was not modified, user chose not to save it
        or file was saved successfully
    */
    private boolean checkSaveGame()
    {
        if (! isModified())
            return true;
        if (m_saveQuestion == null)
            m_saveQuestion = new OptionalMessage(this);
        int result =
            m_saveQuestion.showYesNoCancelQuestion("Save current game?");
        switch (result)
        {
        case 0:
            if (m_file == null)
                return saveDialog();
            else
                return save(m_file);
        case 1:
            m_game.clearModified();
            return true;
        case 2:
            return false;
        default:
            assert(false);
            return true;
        }
    }
    
    /** Check if command is in progress or setup or score mode. */
    private boolean checkSpecialMode()
    {
        if (! checkCommandInProgress())
            return false;
        // TODO: maybe automatically leave setup or score mode instead?
        if (m_setupMode)
        {
            showError("Cannot execute while in setup mode");
            return false;
        }
        if (m_scoreMode)
        {
            showError("Cannot execute while in score mode");
            return false;
        }
        return true;
    }

    private void clearStatus()
    {
        m_statusBar.clear();
    }

    private void close()
    {
        if (isCommandInProgress() && ! showQuestion("Kill program?"))
                return;
        if (! checkSaveGame())
            return;
        saveSession();        
        if (m_gtp != null)
        {
            m_analyzeCommand = null;
            detachProgram();
        }
        dispose();
        System.exit(0);
    }

    private void computerMoved()
    {
        if (! endLengthyCommand())
            return;
        if (m_beepAfterMove)
            Toolkit.getDefaultToolkit().beep();
        try
        {
            String response = m_gtp.getResponse();
            GoColor toMove = getToMove();
            checkLostOnTime(toMove);
            boolean gameTreeChanged = false;
            if (response.equalsIgnoreCase("resign"))
            {
                if (! isComputerBoth())
                    showInfo(m_name + " resigns");
                m_resigned = true;
                setResult((toMove == GoColor.BLACK ? "W" : "B") + "+Resign");
            }
            else
            {
                GoPoint point = GtpUtil.parsePoint(response, getBoardSize());
                ConstBoard board = getBoard();
                if (point != null)
                {
                    if (board.getColor(point) != GoColor.EMPTY)
                    {
                        showWarning("Program played move on non-empty point");
                        m_computerBlack = false;
                        m_computerWhite = false;
                    }
                    else if (board.isKo(point))
                    {
                        showWarning("Program violated Ko rule");
                        m_computerBlack = false;
                        m_computerWhite = false;
                    }
                }
                Move move = Move.get(point, toMove);
                m_game.play(move);
                updateModified();                
                m_gtp.updateAfterGenmove(getBoard());
                if (point == null && ! isComputerBoth())
                    showInfo(m_name + " passes");
                m_resigned = false;
                gameTreeChanged = true;
                ConstNode currentNode = getCurrentNode();
                if (currentNode.getFatherConst().getNumberChildren() == 1)
                {
                    if (m_gameTreeViewer != null)
                        m_gameTreeViewer.addNewSingleChild(currentNode);
                    gameTreeChanged = false;
                }
            }
            boolean doCheckComputerMove
                = (! m_isSingleMove
                   && ! (isComputerBoth() && m_interruptComputerBoth));
            boardChangedBegin(doCheckComputerMove, gameTreeChanged);
        }
        catch (GtpError e)
        {
            showError(e);
            clearStatus();
        }
    }

    private boolean computerToMove()
    {
        if (getToMove() == GoColor.BLACK)
            return m_computerBlack;
        else
            return m_computerWhite;
    }

    private ContextMenu createContextMenu(GoPoint point)
    {
        ContextMenu.Listener listener = new ContextMenu.Listener()
            {
                public void clearAnalyze()
                {
                    GoGui.this.clearAnalyzeCommand();
                }

                public void editLabel(GoPoint point)
                {
                    GoGui.this.editLabel(point);
                }

                public void mark(GoPoint point, MarkType type,
                                 boolean mark)
                {
                    GoGui.this.mark(point, type, mark);
                }

                public void setAnalyzeCommand(AnalyzeCommand command)
                {
                    GoGui.this.setAnalyzeCommand(command, false, true, true);
                }
            };
        ArrayList supportedCommands = null;
        boolean noProgram = (m_gtp == null);
        if (! noProgram)
            supportedCommands = m_gtp.getSupportedCommands();
        return new ContextMenu(point, noProgram, supportedCommands,
                               m_programAnalyzeCommands,
                               m_guiBoard.getMark(point),
                               m_guiBoard.getMarkCircle(point),
                               m_guiBoard.getMarkSquare(point),
                               m_guiBoard.getMarkTriangle(point),
                               listener);
    }

    private void createThumbnail(File file)
    {
        if (! ThumbnailPlatform.checkThumbnailSupport())
            return;
        // Thumbnail creation does not work on GNU classpath 0.90 yet
        if (Platform.isGnuClasspath())
            return;
        String path = file.getAbsolutePath();
        if (! path.startsWith("/tmp") && ! path.startsWith("/var/tmp"))
        {
            try
            {
                m_thumbnailCreator.create(file);
            }
            catch (ThumbnailCreator.Error e)
            {
            }
        }
    }

    private void currentNodeChanged()
    {
        updateFromGoBoard();
        if (m_gtp != null)
        {
            try
            {
                m_gtp.synchronize(getBoard());
            }
            catch (GtpError e)
            {
                showError(e);
                checkProgramInSync();
            }
        }
    }

    private void detachProgram()
    {
        if (isCommandInProgress())
        {
            m_gtp.destroyGtp();
            m_gtp.close();
        }
        else
        {
            if (m_gtp != null && ! m_gtp.isProgramDead())
            {
                // Some programs do not handle closing the GTP stream
                // correctly, so we send a quit before
                try
                {
                    if (m_gtp.isCommandSupported("quit"))
                        m_gtp.send("quit");
                }
                catch (GtpError e)
                {
                }
                m_gtp.close();
            }
        }
        saveSession();
        if (m_analyzeCommand != null)
            clearAnalyzeCommand();
        m_gtp = null;
        m_name = null;
        m_version = null;
        m_gtpShell.dispose();
        m_gtpShell = null;
        if (m_analyzeDialog != null)
        {
            m_analyzeDialog.saveRecent();
            m_analyzeDialog.dispose();
            m_analyzeDialog = null;
        }
        resetBoard();
        clearStatus();
        setTitle();
    }

    private void editLabel(GoPoint point)
    {
        String value = getCurrentNode().getLabel(point);
        value = JOptionPane.showInputDialog(this, "Label " + point, value);
        if (value == null)
            return;
        m_game.setLabel(point, value);
        m_guiBoard.setLabel(point, value);
        updateModified();                
        updateGuiBoard();
    }

    private boolean endLengthyCommand()
    {
        m_statusBar.clearProgress();
        clearStatus();
        m_menuBar.setNormalMode();
        if (m_gtpShell != null)
            m_gtpShell.setCommandInProgess(false);
        // Program could have been killed in cbInterrupt
        if (m_gtp == null)
            return false;
        GtpError error = m_gtp.getException();
        if (error != null)
        {
            showError(error);
            return false;
        }
        return true;
    }

    private void forward(int n)
    {
        assert(n >= 0);
        ConstNode node = getCurrentNode();
        for (int i = 0; i < n; ++i)
        {
            ConstNode child = node.getChildConst();
            if (child == null)
                break;
            node = child;
        }
        gotoNode(node);
    }

    private void generateMove(boolean isSingleMove)
    {
        GoColor toMove = getToMove();
        String command;
        if (NodeUtil.isInCleanup(getCurrentNode())
            && m_gtp.isCommandSupported("kgs-genmove_cleanup"))
        {
            command = "kgs-genmove_cleanup";
            if (toMove == GoColor.BLACK)
                command += " b";
            else if (toMove == GoColor.WHITE)
                command += " w";
            else
                assert(false);
        }
        else
        {
            command = m_gtp.getCommandGenmove(toMove);
        }
        m_isSingleMove = isSingleMove;
        Runnable callback = new Runnable()
            {
                public void run()
                {
                    computerMoved();
                }
            };
        runLengthyCommand(command, callback);
    }

    private ConstBoard getBoard()
    {
        return m_game.getBoard();
    }

    private int getBoardSize()
    {
        return m_game.getSize();
    }

    private ConstClock getClock()
    {
        return m_game.getClock();
    }

    private ConstNode getCurrentNode()
    {
        return m_game.getCurrentNode();
    }

    private ConstGameInformation getGameInformation()
    {
        return m_game.getGameInformation();
    }

    private Komi getPrefsKomi()
    {
        try
        {
            String s = m_prefs.get("komi", "6.5");
            return Komi.parseKomi(s);
        }
        catch (Komi.InvalidKomi e)
        {
            return null;
        }
    }

    private int getRules()
    {
        return getGameInformation().parseRules();
    }

    private GoColor getToMove()
    {
        return m_game.getToMove();
    }

    private ConstGameTree getTree()
    {
        return m_game.getTree();
    }

    private void gotoNode(ConstNode node)
    {
        // GameTreeViewer is not disabled in score mode
        if (m_scoreMode)
            return;
        m_game.gotoNode(node);
        currentNodeChanged();
    }

    private void humanMoved(Move move)
    {
        GoPoint point = move.getPoint();
        if (point != null && getBoard().getColor(point) != GoColor.EMPTY)
            return;
        if (point != null)
            paintImmediately(point, move.getColor(), true);
        if (m_gtp != null && ! isOutOfSync())
        {
            try
            {
                m_gtp.updateHumanMove(getBoard(), move);
            }
            catch (GtpError e)
            {
                showError(e);
                boardChangedBegin(false, false);
                return;
            }
        }
        boolean newNodeCreated = false;
        ConstNode node = NodeUtil.getChildWithMove(getCurrentNode(), move);
        if (node == null)
        {
            newNodeCreated = true;
            m_game.play(move);
        }
        else
        {
            m_game.haltClock();
            m_game.gotoNode(node);            
        }
        updateModified();                
        checkLostOnTime(move.getColor());
        m_resigned = false;
        boolean gameTreeChanged = newNodeCreated;
        ConstNode currentNode = getCurrentNode();
        if (newNodeCreated
            && currentNode.getFatherConst().getNumberChildren() == 1)
        {
            if (m_gameTreeViewer != null)
                m_gameTreeViewer.addNewSingleChild(currentNode);
            gameTreeChanged = false;
        }
        boardChangedBegin(true, gameTreeChanged);
    }

    private void importTextPosition(Reader reader)
    {
        try
        {
            TextParser parser = new TextParser();
            parser.parse(reader);
            GameTree tree =
                NodeUtil.makeTreeFromPosition(null, parser.getBoard());
            m_game.init(tree);
        }
        catch (ParseError e)
        {
            showError("Import failed", e);
        }
        m_guiBoard.initSize(getBoard().getSize());
        initGtp();
        updateModified();
        boardChangedBegin(false, true);
    }

    private void initGame(int size)
    {
        if (size != getBoardSize())
        {
            m_guiBoard.initSize(size);
            restoreMainWindow();
            if (m_gtpShell != null)
                restoreSize(m_gtpShell, "shell");
            if (m_analyzeDialog != null)
            {
                restoreSize(m_analyzeDialog, "analyze");
                m_analyzeDialog.setBoardSize(size);
            }
            if (m_gameTreeViewer != null)
                restoreSize(m_gameTreeViewer, "tree");
        }
        ArrayList handicap = getBoard().getHandicapStones(m_handicap);
        if (handicap == null)
            showWarning("Handicap stone locations not\n" +
                        "defined for this board size");
        m_game.init(size, getPrefsKomi(), handicap, m_prefs.get("rules", ""),
                    m_timeSettings);
        updateFromGoBoard();
        resetBoard();
        m_game.resetClock();
        m_lostOnTimeShown = false;
        updateModified();                
        m_resigned = false;
        m_pattern = null;
    }

    private boolean initGtp()
    {
        if (m_gtp != null)
        {
            try
            {
                m_gtp.initSynchronize(getBoard());
            }
            catch (GtpError error)
            {
                showError(error);
                return false;
            }
        }
        setKomi(getGameInformation().getKomi());
        setRules();
        setTimeSettings();
        currentNodeChanged();
        return ! isOutOfSync();
    }

    private void initialize()
    {
        if (m_file == null)
            newGame(getBoardSize());
        else
            newGameFile(getBoardSize(), m_move);
        if (! m_prefs.getBoolean("show-info-panel", true))
            showInfoPanel(true);
        if (m_prefs.getBoolean("show-toolbar", true))
            showToolbar(true);
        restoreMainWindow();
        // Attaching a program can take some time, so we want to make
        // the window visible, but not draw the window content yet
        getLayeredPane().setVisible(false);
        setVisible(true);
        m_bookmarks = Bookmark.load();
        m_menuBar.setBookmarks(m_bookmarks);
        if (m_program != null)
            attachProgram(m_program);
        setTitle();
        updateGameInfo(true);
        registerSpecialMacHandler();
        initProgressBarTimer();
        // Children dialogs should be set visible after main window, otherwise
        // they get minimize window buttons and a taskbar entry (KDE 3.4)
        if (m_gtpShell != null && m_session.isVisible("shell"))
            actionToggleShowShell();
        if (m_session.isVisible("tree"))
            actionToggleShowTree();
        if (m_session.isVisible("analyze"))
            actionToggleShowAnalyzeDialog();
        if (! m_initAnalyze.equals(""))
        {
            AnalyzeCommand analyzeCommand =
                AnalyzeCommand.get(this, m_initAnalyze);
            if (analyzeCommand == null)
                showError("Unknown analyze command \"" + m_initAnalyze
                          + "\"");
            else
                initAnalyzeCommand(analyzeCommand, false, true);
        }
        setTitleFromProgram();
        updateViews();
        getLayeredPane().setVisible(true);
        toFrontLater();
        checkComputerMove();
    }

    private void initProgressBarTimer()
    {
        m_progressBarTimer = new Timer(500, new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    if (isCommandInProgress()
                        && ! m_statusBar.isProgressShown())
                        m_statusBar.setProgress(-1);
                }
            });
        m_progressBarTimer.setRepeats(false);
        m_progressBarTimer.stop();
    }

    private void initScore(GoPoint[] isDeadStone)
    {
        resetBoard();
        GuiBoardUtil.scoreBegin(m_guiBoard, m_countScore, getBoard(),
                                isDeadStone);
        m_scoreMode = true;
        if (m_scoreDialog == null)
            m_scoreDialog = new ScoreDialog(this, this);
        Komi komi = getGameInformation().getKomi();
        m_scoreDialog.showScore(m_countScore.getScore(komi, getRules()));
        m_scoreDialog.setVisible(true);
        showStatus("Please mark dead groups");
    }

    private void invokeAndWait(Runnable runnable)
    {
        try
        {
            SwingUtilities.invokeAndWait(runnable);
        }
        catch (InterruptedException e)
        {
            System.err.println("Thread interrupted");
        }
        catch (java.lang.reflect.InvocationTargetException e)
        {
            System.err.println("InvocationTargetException");
        }
    }

    private boolean isCommandInProgress()
    {
        if (m_gtp == null)
            return false;
        return m_gtp.isCommandInProgress();
    }

    private boolean isComputerBoth()
    {
        return (m_computerBlack && m_computerWhite);
    }

    private boolean isComputerNone()
    {
        return (! m_computerBlack && ! m_computerWhite);
    }

    private boolean isOutOfSync()
    {
        return (m_gtp != null && m_gtp.isOutOfSync());
    }

    private boolean loadFile(File file, int move)
    {
        try
        {
            FileInputStream in = new FileInputStream(file);
            LoadFileRunnable runnable = new LoadFileRunnable(in, file);
            if (file.length() > 500000)
            {
                newGame(getBoardSize()); // Frees space if already large tree
                GuiUtil.runProgress(this, "Loading...", runnable);
            }
            else
                runnable.run(null);
            SgfReader reader = runnable.getReader();
            GameInformation gameInformation =
                reader.getGameTree().getGameInformation();
            initGame(gameInformation.getBoardSize());
            m_menuBar.addRecent(file);
            m_game.init(reader.getGameTree());
            initGtp();
            if (move > 0)
                forward(move);            
            setFile(file);
            String warnings = reader.getWarnings();
            if (warnings != null)
                showWarning("File " + file.getName() + ":\n" + warnings);
            SimpleDialogs.setLastFile(file);
            m_computerBlack = false;
            m_computerWhite = false;
            createThumbnail(file);
        }
        catch (FileNotFoundException e)
        {
            showError("File not found:\n" + file);
            return false;
        }
        catch (SgfReader.SgfError e)
        {
            showError("Could not read file:", e);
            return false;
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            assert(false);
            return false;
        }
        return true;
    }

    public void mark(GoPoint point, MarkType type, boolean mark)
    {
        if (mark)
            m_game.addMarked(point, type);
        else
            m_game.removeMarked(point, type);
        if (type == MarkType.MARK)
            m_guiBoard.setMark(point, mark);
        else if (type == MarkType.CIRCLE)
            m_guiBoard.setMarkCircle(point, mark);
        else if (type == MarkType.SQUARE)
            m_guiBoard.setMarkSquare(point, mark);
        else if (type == MarkType.TRIANGLE)
            m_guiBoard.setMarkTriangle(point, mark);        
        updateModified();                
        updateGuiBoard();
    }

    private void newGame(int size)
    {
        initGame(size);
        initGtp();
        updateFromGoBoard();
        updateViews();
        setTitle();
        setTitleFromProgram();
        clearStatus();
    }

    private void newGame(int size, boolean startClock)
    {
        if (! checkSaveGame())
            return;
        setFile(null);
        newGame(size);
        if (m_computerBlack || m_computerWhite)
        {
            m_computerBlack = false;
            m_computerWhite = false;
            if (m_handicap == 0)
                m_computerWhite = true;
            else
                m_computerBlack = true;
        }
        if (startClock)
            m_game.startClock();
        boardChangedBegin(true, true);
    }

    private void newGameFile(int size, int move)
    {
        initGame(size);
        loadFile(m_file, move);
        updateGameInfo(true);
        updateFromGoBoard();
        updateViews();
    }

    /** Paint point immediately to pretend better responsiveness.
        Necessary because waiting for a repaint of the Go board can be slow
        due to the updating game tree or response to GTP commands.
    */
    private void paintImmediately(GoPoint point, GoColor color, boolean isMove)
    {
        m_guiBoard.setColor(point, color);
        if (isMove && m_showLastMove)
            m_guiBoard.markLastMove(point);
        m_guiBoard.paintImmediately(point);
    }

    private void registerSpecialMacHandler()
    {        
        if (! Platform.isMac())
            return;
        Platform.SpecialMacHandler handler = new Platform.SpecialMacHandler()
            {
                public boolean handleAbout()
                {
                    assert(SwingUtilities.isEventDispatchThread());
                    actionAbout();
                    return true;
                }
                
                public boolean handleOpenFile(String filename)
                {
                    assert(SwingUtilities.isEventDispatchThread());
                    if (! checkSaveGame())
                        return true;
                    loadFile(new File(filename), -1);
                    boardChangedBegin(false, true);
                    return true;
                }
                
                public boolean handleQuit()
                {
                    assert(SwingUtilities.isEventDispatchThread());
                    close();
                    // close() calls System.exit() if not cancelled
                    return false;
                }
            };
        Platform.registerSpecialMacHandler(handler);
    }

    private void resetBoard()
    {
        clearStatus();
        m_guiBoard.clearAll();
        updateFromGoBoard();
        updateGuiBoard();
    }
    
    private void restoreMainWindow()
    {
        setState(Frame.NORMAL);
        m_session.restoreLocation(this, "main", getBoardSize());
        Dimension preferredCommentSize = null;
        String path = "windows/main/size-" + getBoardSize() + "/fieldsize";
        int fieldSize = m_prefs.getInt(path, -1);
        if (fieldSize > 0)
            m_guiBoard.setPreferredFieldSize(new Dimension(fieldSize,
                                                           fieldSize));
        path = "windows/main/size-" + getBoardSize() + "/comment";
        int width = m_prefs.getInt(path + "/width", -1);
        int height = m_prefs.getInt(path + "/height", -1);
        if (width > 0 && height > 0)
        {
            preferredCommentSize = new Dimension(width, height);
            m_comment.setPreferredSize(preferredCommentSize);
        }
        m_splitPane.resetToPreferredSizes();
        pack();
        // To avoid smallish empty borders (less than one field size) on top
        // and bottom borders of the board we adjust the comment size slightly
        // if necessary
        if (m_infoPanel.getHeight() - m_guiBoard.getHeight() < 2 * fieldSize
            && preferredCommentSize != null && fieldSize > 0)
        {
            preferredCommentSize.height -= 2 * fieldSize;
            m_comment.setPreferredSize(preferredCommentSize);
            m_splitPane.resetToPreferredSizes();
            pack();
            }
    }

    private void restoreSize(Window window, String name)
    {
        m_session.restoreSize(window, name, getBoardSize());
    }

    private void runLengthyCommand(String cmd, Runnable callback)
    {
        assert(m_gtp != null);
        m_gtp.send(cmd, callback);
        beginLengthyCommand();
    }

    /** Save game to file.
        @return true If successfully saved.
    */
    private boolean save(File file)
    {
        OutputStream out;
        try
        {
            out = new FileOutputStream(file);
        }
        catch (FileNotFoundException e)
        {
            showError("Saving file failed", e);
            return false;
        }
        new SgfWriter(out, getTree(), "GoGui", Version.get());
        m_menuBar.addRecent(file);
        createThumbnail(file);
        setFile(file);
        m_game.clearModified();
        updateModified();                
        return true;
    }

    private boolean saveDialog()
    {
        File file = SimpleDialogs.showSaveSgf(this);
        if (file == null)
            return false;
        return save(file);
    }

    private void savePosition(File file) throws FileNotFoundException
    {
        OutputStream out = new FileOutputStream(file);
        new SgfWriter(out, getBoard(), "GoGui", Version.get());
        m_menuBar.addRecent(file);
    }

    private void saveSession()
    {
        Bookmark.save(m_bookmarks);
        if (m_gtpShell != null)
            m_gtpShell.saveHistory();
        if (m_analyzeDialog != null)
            m_analyzeDialog.saveRecent();
        m_session.saveLocation(this, "main", getBoardSize());
        if (m_help != null)
            saveSize(m_help, "help");
        saveSizeAndVisible(m_gameTreeViewer, "tree");
        if (m_gtp != null)
        {
            saveSizeAndVisible(m_gtpShell, "shell");
            saveSizeAndVisible(m_analyzeDialog, "analyze");
        }
        if (GuiUtil.isNormalSizeMode(this))
        {            
            String name = "windows/main/size-" + getBoardSize() + "/fieldsize";
            m_prefs.putInt(name, m_guiBoard.getFieldSize().width);
            name = "windows/main/size-" + getBoardSize() + "/comment/width";
            m_prefs.putInt(name, m_comment.getWidth());
            name = "windows/main/size-" + getBoardSize() + "/comment/height";
            m_prefs.putInt(name, m_comment.getHeight());
        }
    }

    private void saveSize(JDialog dialog, String name)
    {
        m_session.saveSize(dialog, name, getBoardSize());
    }

    private void saveSizeAndVisible(JDialog dialog, String name)
    {
        m_session.saveSizeAndVisible(dialog, name, getBoardSize());
    }

    private void sendGtp(Reader reader)
    {
        if (m_gtp == null)
            return;
        java.io.BufferedReader in;
        in = new BufferedReader(reader);
        try
        {
            while (true)
            {
                try
                {
                    String line = in.readLine();
                    if (line == null)
                        break;
                    if (! m_gtpShell.send(line, this, true))
                        break;
                }
                catch (IOException e)
                {
                    showError("Error reading file");
                    break;
                }
            }
        }
        finally
        {
            try
            {
                in.close();
            }
            catch (IOException e)
            {
            }
        }
    }

    private void sendGtpFile(File file)
    {
        try
        {
            sendGtp(new FileReader(file));
        }
        catch (FileNotFoundException e)
        {
            showError("File not found: " + e.getMessage());
        }
    }

    private void sendGtpString(String commands)
    {        
        commands = commands.replaceAll("\\\\n", "\n");
        sendGtp(new StringReader(commands));
    }

    private void setBoardCursor(int type)
    {
        setCursor(m_guiBoard, type);
        setCursor(m_infoPanel, type);
    }

    private void setBoardCursorDefault()
    {
        setCursorDefault(m_guiBoard);
        setCursorDefault(m_infoPanel);
    }

    private void setCursor(Component component, int type)
    {
        Cursor cursor = Cursor.getPredefinedCursor(type);
        component.setCursor(cursor);
    }

    private void setCursorDefault(Component component)
    {
        component.setCursor(Cursor.getDefaultCursor());
    }

    private void setFile(File file)
    {
        m_file = file;
        updateViews();
        setTitle();
    }

    private void setKomi(Komi komi)
    {
        GuiGtpUtil.sendKomi(this, komi, m_name, m_gtp);
    }

    private void setResult(String result)
    {
        String oldResult = getGameInformation().getResult();
        if (! (oldResult == null || oldResult.equals("")
               || oldResult.equals(result))
            && ! showQuestion("Overwrite old result " + oldResult + "\n" +
                              "with " + result + "?"))
            return;
        m_game.setResult(result);
    }

    private void setRules()
    {
        GuiGtpUtil.sendRules(getRules(), m_gtp);
    }

    private void setTimeSettings()
    {
        if (m_gtp == null)
            return;
        TimeSettings timeSettings = getGameInformation().getTimeSettings();
        if (timeSettings == null)
            return;
        if (! m_gtp.isCommandSupported("time_settings"))
            return;
        m_game.setTimeSettings(timeSettings);
        String command = GtpUtil.getTimeSettingsCommand(timeSettings);
        try
        {
            m_gtp.send(command);
        }
        catch (GtpError e)
        {
            showError(e);
        }
    }

    private void setTitle()
    {
        if (m_titleFromProgram != null)
        {
            setTitle(m_titleFromProgram);
            return;
        }
        String appName = "GoGui";        
        if (m_gtp != null)
            appName = m_name;
        String filename = null;
        if (m_file != null)
        {
            filename = m_file.getName();
            if (isModified())
                filename = filename + " [modified]";
        }
        else if (isModified())
            filename = "[modified]";
        String gameName = getGameInformation().suggestGameName();
        if (gameName != null)
        {
            if (filename != null)
                gameName = filename + "  " + gameName;
        }
        else if (filename != null)
            gameName = filename;
        if (gameName == null)
            setTitle(appName);        
        else
            setTitle(gameName + " - " + appName);
    }

    private void setTitleFromProgram()
    {
        if (m_gtp == null)
            m_titleFromProgram = null;
        else
            m_titleFromProgram = m_gtp.getTitleFromProgram();
        if (m_titleFromProgram != null)
            setTitle(m_titleFromProgram);
    }

    private void setup(GoPoint point, GoColor color)
    {
        assert(point != null);
        m_game.setup(point, color);
    }

    private void setupDone()
    {
        m_setupMode = false;
        m_menuBar.setNormalMode();
        m_game.setToMove(getToMove());
        initGtp();
        setFile(null);
        updateGameInfo(true);
        boardChangedBegin(false, false);
    }

    private void showError(String message, Exception e)
    {
        SimpleDialogs.showError(this, message, e);
    }

    private void showError(GtpError error)
    {        
        GuiGtpUtil.showError(this, m_name, error);
    }

    private void showError(String message)
    {
        SimpleDialogs.showError(this, message);
    }

    private void showGameFinished()
    {
        if (m_gameFinishedMessage == null)
            m_gameFinishedMessage = new OptionalMessage(this);
        m_gameFinishedMessage.showMessage("Game finished");
    }

    private void showInfo(String message)
    {
        SimpleDialogs.showInfo(this, message);
    }

    private void showInfoPanel(boolean enable)
    {
        if (enable == m_showInfoPanel)
            return;
        m_prefs.putBoolean("show-info-panel", enable);
        m_showInfoPanel = enable;
        if (enable)
        {
            m_innerPanel.remove(m_guiBoard);
            m_splitPane.add(m_guiBoard);
            m_innerPanel.add(m_splitPane);
        }
        else
        {
            m_splitPane.remove(m_guiBoard);
            m_innerPanel.remove(m_splitPane);
            m_innerPanel.add(m_guiBoard);
        }
        m_splitPane.resetToPreferredSizes();
        pack();
    }

    private boolean showQuestion(String message)
    {
        return SimpleDialogs.showQuestion(this, message);
    }

    private void showStatus(String text)
    {
        m_statusBar.setText(text);
    }

    private void showStatusSelectPointList()
    {
        showStatus("Select points for " + m_analyzeCommand.getLabel()
                   + " (last point with right button or modifier key down)");
    }

    private void showStatusSelectTarget()
    {
        showStatus("Select a target for "
                   + m_analyzeCommand.getResultTitle());
    }

    private void showToolbar(boolean enable)
    {
        if (enable == m_showToolbar)
            return;
        m_prefs.putBoolean("show-toolbar", enable);
        m_showToolbar = enable;
        if (enable)
        {
            getContentPane().add(m_toolBar, BorderLayout.NORTH);
            m_menuBar.setHeaderStyleSingle(false);
        }
        else
        {
            getContentPane().remove(m_toolBar);
            m_menuBar.setHeaderStyleSingle(true);
        }
        m_splitPane.resetToPreferredSizes();
        pack();
    }

    private void showWarning(String message)
    {
        SimpleDialogs.showWarning(this, message);
    }

    private void toFrontLater()
    {
        // Calling toFront() directly does not give the focus to this
        // frame, if dialogs are open
        SwingUtilities.invokeLater(new Runnable() {
                public void run()
                {
                    toFront();
                }
            });
    }

    private void updateViews()
    {
        m_actions.update();
        m_toolBar.update();
        GoGuiUtil.updateMoveText(m_statusBar, getGame());
    }

    private void updateFromGoBoard()
    {
        GuiBoardUtil.updateFromGoBoard(m_guiBoard, getBoard(), m_showLastMove);
        if (getCurrentNode().getMove() == null)
            m_guiBoard.markLastMove(null);
    }

    private void updateGameInfo(boolean gameTreeChanged)
    {
        m_gameInfo.update(getCurrentNode(), getBoard());
        updateGameTree(gameTreeChanged);
        m_comment.setComment(getCurrentNode().getComment());
        updateGuiBoard();
        if (m_analyzeDialog != null)
            m_analyzeDialog.setSelectedColor(getToMove());
    }

    private void updateGameTree(boolean gameTreeChanged)
    {
        if (m_gameTreeViewer == null)
            return;
        if (! gameTreeChanged)
        {
            m_gameTreeViewer.update(getCurrentNode());
            return;
        }
        m_gameTreeViewer.update(getTree(), getCurrentNode());
    }

    private void updateGuiBoard()
    {
        if (m_showVariations)
        {
            ArrayList childrenMoves
                = NodeUtil.getChildrenMoves(getCurrentNode());
            GuiBoardUtil.showChildrenMoves(m_guiBoard, childrenMoves);
        }
        GuiBoardUtil.showMarkup(m_guiBoard, getCurrentNode());
    }

    private void updateModified()
    {
        getRootPane().putClientProperty("windowModified",
                                        Boolean.valueOf(isModified()));
        setTitle();
    }    
}
