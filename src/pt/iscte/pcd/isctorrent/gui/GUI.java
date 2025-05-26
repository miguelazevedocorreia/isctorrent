package pt.iscte.pcd.isctorrent.gui;

import pt.iscte.pcd.isctorrent.core.IscTorrent;
import pt.iscte.pcd.isctorrent.gui.dialogs.ConnectionDialog;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;

import javax.swing.*;
import java.awt.*;
import java.util.List;


public class GUI extends JFrame {
    private final IscTorrent torrent;
    private final JTextField searchField;
    private final JList<FileSearchResultDisplay> resultsList;
    private final DefaultListModel<FileSearchResultDisplay> resultsModel;
    private final JList<String> connectionsList;
    private final DefaultListModel<String> connectionsModel;

    public GUI(IscTorrent torrent, int port) {
        this.torrent = torrent;
        setTitle("IscTorrent - Porta: " + port);
        setSize(700, 450);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Painel de busca
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchField = new JTextField();
        JButton searchButton = new JButton("Procurar");
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        // Lista de resultados
        resultsModel = new DefaultListModel<>();
        resultsList = new JList<>(resultsModel);
        resultsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane scrollPane = new JScrollPane(resultsList);

        // Lista de conexões
        connectionsModel = new DefaultListModel<>();
        connectionsList = new JList<>(connectionsModel);
        connectionsList.setBorder(BorderFactory.createTitledBorder("Conexões Ativas"));
        JScrollPane connectionsScroll = new JScrollPane(connectionsList);
        connectionsScroll.setPreferredSize(new Dimension(200, 120));

        // Botões
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        JButton downloadButton = new JButton("Transferir");
        JButton connectButton = new JButton("Conectar");
        buttonPanel.add(downloadButton);
        buttonPanel.add(connectButton);

        // Painel direito (botões + conexões)
        JPanel rightPanel = new JPanel(new BorderLayout(0, 5));
        rightPanel.add(buttonPanel, BorderLayout.NORTH);
        rightPanel.add(connectionsScroll, BorderLayout.CENTER);

        // Layout principal
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mainPanel.add(searchPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);

        setContentPane(mainPanel);

        // Eventos
        searchButton.addActionListener(e -> search());
        searchField.addActionListener(e -> search());
        downloadButton.addActionListener(e -> download());
        connectButton.addActionListener(e -> showConnectionDialog());

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void search() {
        String keyword = searchField.getText();
        if (!keyword.isEmpty()) {
            resultsModel.clear();
            torrent.searchFiles(keyword);
        }
    }

    private void download() {
        List<FileSearchResultDisplay> selectedItems = resultsList.getSelectedValuesList();
        if (!selectedItems.isEmpty()) {
            for (FileSearchResultDisplay selected : selectedItems) {
                torrent.startDownload(selected.getResult());
            }
        }
    }

    public synchronized void addSearchResults(List<FileSearchResult> results) {
        SwingUtilities.invokeLater(() -> {
            for (FileSearchResult result : results) {
                boolean found = false;
                for (int i = 0; i < resultsModel.size(); i++) {
                    FileSearchResultDisplay display = resultsModel.getElementAt(i);
                    if (display.getResult().fileName().equals(result.fileName())) {
                        display.incrementNodeCount();
                        resultsModel.setElementAt(display, i); // Atualiza display
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    resultsModel.addElement(new FileSearchResultDisplay(result));
                }
            }
        });
    }

    public void updateConnectionsList() {
        SwingUtilities.invokeLater(() -> {
            connectionsModel.clear();
            List<String> connections = torrent.getConnectionManager().getConnectionsList();
            connections.forEach(connectionsModel::addElement);
        });
    }

    private void showConnectionDialog() {
        ConnectionDialog.ConnectionResult result = ConnectionDialog.showDialog(this);
        if (result != null) {
            torrent.connectToNode(result.address(), result.port());
        }
    }

    // Classe interna para display dos resultados com contagem de nós
    private static class FileSearchResultDisplay {
        private final FileSearchResult result;
        private int nodeCount;

        public FileSearchResultDisplay(FileSearchResult result) {
            this.result = result;
            this.nodeCount = 1;
        }

        public void incrementNodeCount() {
            nodeCount++;
        }

        public FileSearchResult getResult() {
            return result;
        }

        @Override
        public String toString() {
            return String.format("%s (%d bytes) (%d nodes)",
                    result.fileName(),
                    result.fileSize(),
                    nodeCount);
        }
    }
}