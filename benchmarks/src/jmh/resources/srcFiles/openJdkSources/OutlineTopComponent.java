/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package com.sun.hotspot.igv.coordinator;

import com.sun.hotspot.igv.connection.Server;
import com.sun.hotspot.igv.coordinator.actions.*;
import com.sun.hotspot.igv.data.ChangedListener;
import com.sun.hotspot.igv.data.GraphDocument;
import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.serialization.ParseMonitor;
import com.sun.hotspot.igv.data.serialization.Parser;
import com.sun.hotspot.igv.data.serialization.Printer;
import com.sun.hotspot.igv.data.serialization.Printer.GraphContext;
import com.sun.hotspot.igv.data.services.GraphViewer;
import com.sun.hotspot.igv.data.services.InputGraphProvider;
import com.sun.hotspot.igv.settings.Settings;
import com.sun.hotspot.igv.util.LookupHistory;
import com.sun.hotspot.igv.view.EditorTopComponent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.filechooser.FileFilter;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.ErrorManager;
import org.openide.awt.Toolbar;
import org.openide.awt.ToolbarPool;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.explorer.view.BeanTreeView;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 *
 * @author Thomas Wuerthinger
 */
public final class OutlineTopComponent extends TopComponent implements ExplorerManager.Provider, ChangedListener<InputGraphProvider> {

    public static final String PREFERRED_ID = "OutlineTopComponent";
    private static final GraphDocument document = new GraphDocument();
    private static final int WORK_UNITS = 10000;
    private static final FileFilter xmlFileFilter = new FileFilter() {
        @Override
        public boolean accept(File f) {
            return f.getName().toLowerCase().endsWith(".xml") || f.isDirectory();
        }

        @Override
        public String getDescription() {
            return "Graph files (*.xml)";
        }
    };
    private static final Server server = new Server(document, OutlineTopComponent::loadContext);
    public static OutlineTopComponent instance;
    private final Set<FolderNode> selectedFolders = new HashSet<>();
    private ExplorerManager manager;
    private FolderNode root;
    private SaveAction saveAction;
    private SaveAsAction saveAsAction;
    private RemoveAllAction removeAllAction;
    private GraphNode[] selectedGraphs = new GraphNode[0];
    private Path documentPath = null;

    private OutlineTopComponent() {
        initComponents();

        setName(NbBundle.getMessage(OutlineTopComponent.class, "CTL_OutlineTopComponent"));
        setToolTipText(NbBundle.getMessage(OutlineTopComponent.class, "HINT_OutlineTopComponent"));
        initListView();
        initToolbar();
        server.startServer();
    }

    public static GraphDocument getDocument() {
        return document;
    }

    /**
     * Gets default instance. Do not use directly: reserved for *.settings files only,
     * i.e. deserialization routines; otherwise you could get a non-deserialized instance.
     * To obtain the singleton instance, use {@link #findInstance()}.
     */
    private static synchronized OutlineTopComponent getDefault() {
        if (instance == null) {
            instance = new OutlineTopComponent();
        }
        return instance;
    }

    /**
     * Obtain the OutlineTopComponent instance. Never call {@link #getDefault} directly!
     */
    public static synchronized OutlineTopComponent findInstance() {
        TopComponent win = WindowManager.getDefault().findTopComponent(PREFERRED_ID);
        if (win == null) {
            ErrorManager.getDefault().log(ErrorManager.WARNING, "Cannot find Outline component. It will not be located properly in the window system.");
            return getDefault();
        }
        if (win instanceof OutlineTopComponent) {
            return (OutlineTopComponent) win;
        }
        ErrorManager.getDefault().log(ErrorManager.WARNING, "There seem to be multiple components with the '" + PREFERRED_ID + "' ID. That is a potential source of errors and unexpected behavior.");
        return getDefault();
    }

    /**
     * Stores the provided graph document to the designated file path with associated contexts.
     */
    private static void saveGraphDocument(GraphDocument doc, String path) throws IOException {
        List<GraphContext> saveContexts = new ArrayList<>();
        WindowManager manager = WindowManager.getDefault();
        for (Mode mode : manager.getModes()) {
            List<TopComponent> compList = new ArrayList<>(Arrays.asList(manager.getOpenedTopComponents(mode)));
            for (TopComponent comp : compList) {
                if (comp instanceof EditorTopComponent etc) {
                    InputGraph graph = etc.getModel().getGraph();
                    if (graph.isDiffGraph() && graph.getFirstGraph().getGroup() != graph.getSecondGraph().getGroup()) {
                        continue;
                    }
                    GraphContext graphContext = getGraphContext(etc);
                    saveContexts.add(graphContext);
                }
            }
        }

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(path))) {
            Printer.exportGraphDocument(writer, doc, saveContexts);
        }
    }

    private static GraphContext getGraphContext(EditorTopComponent etc) {
        InputGraph openedGraph = etc.getModel().getFirstGraph();
        int posDiff = etc.getModel().getSecondPosition() - etc.getModel().getFirstPosition();
        if (etc.getModel().getHiddenNodes().isEmpty()) {
            return new GraphContext(openedGraph, new AtomicInteger(posDiff), new HashSet<>(), new AtomicBoolean(true));
        } else {
            Set<Integer> visibleNodes = new HashSet<>(etc.getModel().getVisibleNodes());
            return new GraphContext(openedGraph, new AtomicInteger(posDiff), visibleNodes, new AtomicBoolean(false));
        }
    }

    private void initListView() {
        setDocumentPath(null);
        FolderNode.clearGraphNodeMap();
        document.clear();
        root = new FolderNode(document);
        manager = new ExplorerManager();
        manager.setRootContext(root);
        ((BeanTreeView) this.treeView).setRootVisible(false);
        associateLookup(ExplorerUtils.createLookup(manager, getActionMap()));
    }

    private void initToolbar() {
        Toolbar toolbar = new Toolbar();
        toolbar.setBorder((Border) UIManager.get("Nb.Editor.Toolbar.border")); 
        toolbar.setMinimumSize(new Dimension(0, 0)); 

        this.add(toolbar, BorderLayout.NORTH);

        toolbar.add(OpenAction.get(OpenAction.class));
        toolbar.add(ImportAction.get(ImportAction.class));
        toolbar.addSeparator();

        saveAction = SaveAction.get(SaveAction.class);
        saveAction.setEnabled(false);
        toolbar.add(saveAction);
        saveAsAction = SaveAsAction.get(SaveAsAction.class);
        saveAsAction.setEnabled(false);
        toolbar.add(saveAsAction);

        toolbar.addSeparator();
        toolbar.add(RemoveAction.get(RemoveAction.class).createContextAwareInstance(this.getLookup()));
        removeAllAction = RemoveAllAction.get(RemoveAllAction.class);
        removeAllAction.setEnabled(false);
        toolbar.add(removeAllAction);

        for (Toolbar tb : ToolbarPool.getDefault().getToolbars()) {
            tb.setVisible(false);
        }

        document.getChangedEvent().addListener(g -> documentChanged());
    }

    private void documentChanged() {
        boolean enableButton = !document.getElements().isEmpty();
        saveAction.setEnabled(enableButton);
        saveAsAction.setEnabled(enableButton);
        removeAllAction.setEnabled(enableButton);
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return manager;
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_ALWAYS;
    }

    @Override
    public void componentOpened() {
        LookupHistory.addListener(InputGraphProvider.class, this);
        this.requestActive();
    }

    @Override
    public void componentClosed() {
        LookupHistory.removeListener(InputGraphProvider.class, this);
    }

    @Override
    protected String preferredID() {
        return PREFERRED_ID;
    }

    @Override
    public void requestActive() {
        super.requestActive();
        treeView.requestFocus();
    }

    @Override
    public boolean requestFocus(boolean temporary) {
        treeView.requestFocus();
        return super.requestFocus(temporary);
    }

    @Override
    protected boolean requestFocusInWindow(boolean temporary) {
        treeView.requestFocus();
        return super.requestFocusInWindow(temporary);
    }

    @Override
    public void changed(InputGraphProvider lastProvider) {
        for (GraphNode graphNode : selectedGraphs) {
            graphNode.setSelected(false);
        }
        for (FolderNode folderNode : selectedFolders) {
            folderNode.setSelected(false);
        }
        selectedGraphs = new GraphNode[0];
        selectedFolders.clear();
        if (lastProvider != null) {
            InputGraph graph = lastProvider.getGraph();
            if (graph != null) {
                if (graph.isDiffGraph()) {
                    InputGraph firstGraph = graph.getFirstGraph();
                    GraphNode firstNode = FolderNode.getGraphNode(firstGraph);
                    InputGraph secondGraph = graph.getSecondGraph();
                    GraphNode secondNode = FolderNode.getGraphNode(secondGraph);
                    if (firstNode != null && secondNode != null) {
                        selectedGraphs = new GraphNode[]{firstNode, secondNode};
                    }
                } else {
                    GraphNode graphNode = FolderNode.getGraphNode(graph);
                    if (graphNode != null) {
                        selectedGraphs = new GraphNode[]{graphNode};
                    }
                }
            }
        }
        try {
            for (GraphNode graphNode : selectedGraphs) {
                Node parentNode = graphNode.getParentNode();
                if (parentNode instanceof FolderNode) {
                    FolderNode folderNode = (FolderNode) graphNode.getParentNode();
                    folderNode.setSelected(true);
                    selectedFolders.add(folderNode);
                }
                graphNode.setSelected(true);
            }
            manager.setSelectedNodes(selectedGraphs);
        } catch (Exception e) {
            Exceptions.printStackTrace(e);
        }
    }

    @Override
    public boolean canClose() {
        SwingUtilities.invokeLater(() -> {
            clearWorkspace();
            open(); 
            requestActive();
        });
        return true;
    }

    private void setDocumentPath(String path) {
        if (path != null) {
            documentPath = Paths.get(path);
            setHtmlDisplayName("<html><b>" + documentPath.getFileName().toString() + "</b></html>");
            setToolTipText("File: " + path);
        } else {
            documentPath = null;
            setHtmlDisplayName("<html><i>untitled</i></html>");
            setToolTipText("No file");
        }

    }

    /**
     * Clears the workspace by resetting the document path, clearing the document, and resetting the folder structure.
     * After clearing the workspace, it will be ready for new documents.
     */
    public void clearWorkspace() {
        setDocumentPath(null);
        document.clear();
        FolderNode.clearGraphNodeMap();
        root = new FolderNode(document);
        manager.setRootContext(root);
        EditorTopComponent.closeAllInstances();
    }

    /**
     * Opens a file dialog to select and load a graph document.
     * Clears the workspace and adds the loaded document to the workspace.
     **/
    public void openFile() {
        JFileChooser fc = new JFileChooser(Settings.get().get(Settings.DIRECTORY, Settings.DIRECTORY_DEFAULT));
        fc.setFileFilter(xmlFileFilter);
        if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            clearWorkspace();
            String path = fc.getSelectedFile().getAbsolutePath();
            Settings.get().put(Settings.DIRECTORY, path);
            setDocumentPath(path);
            try {
                loadGraphDocument(path, true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private boolean overwriteDialog(String filename) {
        JFrame frame = new JFrame();
        String message = "Do you want to overwrite " + filename + "?";
        int result = JOptionPane.showConfirmDialog(frame, message, "Confirm Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        frame.dispose();
        return result == JOptionPane.YES_OPTION;
    }

    /**
     * Saves the current graph document.
     * If the document has no location, let the user specify the file location.
     */
    public void save() {
        if (documentPath == null) {
            saveAs();
            return;
        }

        String filePath = documentPath.toAbsolutePath().toString();
        if (Files.exists(Paths.get(filePath)) && overwriteDialog(documentPath.getFileName().toString())) {
            try {
                saveGraphDocument(getDocument(), filePath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            saveAs();
        }
    }

    public void saveAs() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save As...");
        fc.setFileFilter(xmlFileFilter);
        fc.setCurrentDirectory(new File(Settings.get().get(Settings.DIRECTORY, Settings.DIRECTORY_DEFAULT)));
        if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getAbsolutePath();

            if (Files.exists(Paths.get(path)) && !overwriteDialog(fc.getSelectedFile().getName())) {
                return; 
            }

            Settings.get().put(Settings.DIRECTORY, path);
            setDocumentPath(path);
            try {
                saveGraphDocument(getDocument(), path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Imports graph documents from one or more XML files.
     * Displays a file chooser dialog to select one or multiple XML files for import.
     * Each selected file is added to the workspace.
     **/
    public void importFromXML() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(xmlFileFilter);
        fc.setCurrentDirectory(new File(Settings.get().get(Settings.DIRECTORY, Settings.DIRECTORY_DEFAULT)));
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            for (final File file : fc.getSelectedFiles()) {
                String path = file.getAbsolutePath();
                Settings.get().put(Settings.DIRECTORY, path);
                try {
                    loadGraphDocument(path, false);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Loads and opens the given graph context (opened graphs and visible nodes).
     */
    private static void loadContext(GraphContext context) {
        final GraphViewer viewer = Lookup.getDefault().lookup(GraphViewer.class);
        if (viewer != null) {
            final int difference = context.posDiff().get();
            final InputGraph firstGraph = context.inputGraph();
            final Set<Integer> visibleNodes = context.visibleNodes();
            final boolean showAll = context.showAll().get();

            SwingUtilities.invokeLater(() -> {
                InputGraph openedGraph = viewer.view(firstGraph, true);
                if (openedGraph != null) {
                    EditorTopComponent etc = EditorTopComponent.findEditorForGraph(firstGraph);
                    if (etc != null) {
                        if (showAll) {
                            etc.getModel().setHiddenNodes(new HashSet<>());
                        } else {
                            etc.getModel().showOnly(visibleNodes);
                        }
                        int firstGraphIdx = firstGraph.getIndex();
                        if (difference > 0) {
                            etc.getModel().setPositions(firstGraphIdx, firstGraphIdx + difference);
                        } else if (difference < 0) {
                            etc.getModel().setPositions(firstGraphIdx + difference, firstGraphIdx);
                        }
                    }
                }
            });
        }
    }

    /**
     * Loads a graph document from the given file path, updating progress via a ProgressHandle.
     * Parse the XML file, add the parsed document to the workspace, and load associated contexts if specified.
     */
    private void loadGraphDocument(String path, boolean loadContext) throws IOException {
        if (Files.notExists(Path.of(path))) {
            return;
        }
        File file = new File(path);
        final FileChannel channel;
        final long start;
        try {
            channel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
            start = channel.size();
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            return;
        }

        final ProgressHandle handle = ProgressHandleFactory.createHandle("Opening file " + file.getName());
        handle.start(WORK_UNITS);

        ParseMonitor monitor = new ParseMonitor() {
            @Override
            public void updateProgress() {
                try {
                    int prog = (int) (WORK_UNITS * (double) channel.position() / (double) start);
                    handle.progress(prog);
                } catch (IOException ignored) {
                }
            }

            @Override
            public void setState(String state) {
                updateProgress();
                handle.progress(state);
            }
        };
        try {
            if (file.getName().endsWith(".xml")) {
                ArrayList<GraphContext> contexts = new ArrayList<>();
                final Parser parser = new Parser(channel, monitor, document, loadContext ? contexts::add : null);
                parser.parse();
                SwingUtilities.invokeLater(() -> {
                    for (Node child : manager.getRootContext().getChildren().getNodes(true)) {
                        ((BeanTreeView) this.treeView).expandNode(child);
                        ((BeanTreeView) this.treeView).collapseNode(child);
                    }
                    requestActive();
                });
                for (GraphContext ctx : contexts) {
                    loadContext(ctx);
                }
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        handle.finish();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    private void initComponents() {

        treeView = new BeanTreeView();

        setLayout(new java.awt.BorderLayout());
        add(treeView, java.awt.BorderLayout.CENTER);
    }

    private javax.swing.JScrollPane treeView;
}
