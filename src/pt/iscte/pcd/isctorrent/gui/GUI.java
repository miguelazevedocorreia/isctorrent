package pt.iscte.pcd.isctorrent.gui;

import pt.iscte.pcd.isctorrent.core.IscTorrent;
import pt.iscte.pcd.isctorrent.gui.dialogs.ConnectionDialog;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

// interface gráfica
public class GUI extends JFrame {
    private final IscTorrent torrent;
    private final JTextField searchField; // campo de pesquisa
    private final JList<FileSearchResultDisplay> resultsList; // lista de resultados
    private final DefaultListModel<FileSearchResultDisplay> resultsModel;
    private final JList<String> connectionsList; // lista de conexões ativas
    private final DefaultListModel<String> connectionsModel;

    public GUI(IscTorrent torrent, int port) {
        this.torrent = torrent;
        setTitle("IscTorrent - Porta: " + port);
        setSize(700, 450);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // painel de pesquisa no topo
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchField = new JTextField();
        JButton searchButton = new JButton("Procurar");
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        // lista de resultados à esquerda
        resultsModel = new DefaultListModel<>();
        resultsList = new JList<>(resultsModel);
        resultsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION); // seleção múltipla
        JScrollPane scrollPane = new JScrollPane(resultsList);

        // lista de conexões ativas
        connectionsModel = new DefaultListModel<>();
        connectionsList = new JList<>(connectionsModel);
        connectionsList.setBorder(BorderFactory.createTitledBorder("Conexões Ativas"));
        JScrollPane connectionsScroll = new JScrollPane(connectionsList);
        connectionsScroll.setPreferredSize(new Dimension(200, 120));

        // botões à direita
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        JButton downloadButton = new JButton("Transferir");
        JButton connectButton = new JButton("Conectar");
        buttonPanel.add(downloadButton);
        buttonPanel.add(connectButton);

        // painel direito com botões e conexões
        JPanel rightPanel = new JPanel(new BorderLayout(0, 5));
        rightPanel.add(buttonPanel, BorderLayout.NORTH);
        rightPanel.add(connectionsScroll, BorderLayout.CENTER);

        // layout principal
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mainPanel.add(searchPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);

        setContentPane(mainPanel);

        // eventos dos botões
        searchButton.addActionListener(e -> search());
        searchField.addActionListener(e -> search()); // Enter no campo também pesquisa
        downloadButton.addActionListener(e -> download());
        connectButton.addActionListener(e -> showConnectionDialog());

        setLocationRelativeTo(null);
        setVisible(true);
    }

    // inicia pesquisa por palavra-chave
    private void search() {
        String keyword = searchField.getText().trim();
        if (!keyword.isEmpty()) {
            resultsModel.clear(); // limpa resultados anteriores
            torrent.searchFiles(keyword);
        }
    }

    // inicia download dos ficheiros selecionados
    private void download() {
        List<FileSearchResultDisplay> selectedItems = resultsList.getSelectedValuesList();
        if (!selectedItems.isEmpty()) {
            for (FileSearchResultDisplay selected : selectedItems) {
                List<FileSearchResult> allResults = selected.getAllResults();
                torrent.startDownloadFromMultipleNodes(allResults); // download com múltiplos nós
            }
        }
    }

    // adiciona resultados à lista - thread-safe
    public void addSearchResults(List<FileSearchResult> results) {
        SwingUtilities.invokeLater(() -> {
            for (FileSearchResult result : results) {
                boolean found = false;
                // agrupa ficheiros com mesmo nome
                for (int i = 0; i < resultsModel.size(); i++) {
                    FileSearchResultDisplay display = resultsModel.getElementAt(i);
                    if (display.fileName.equals(result.fileName())) {
                        display.addResult(result); // adiciona nó à lista do ficheiro
                        resultsModel.setElementAt(display, i); // atualiza display
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

    // atualiza lista de conexões ativas
    public void updateConnectionsList() {
        SwingUtilities.invokeLater(() -> {
            connectionsModel.clear();
            List<String> connections = torrent.getConnectionManager().getConnectionsList();
            connections.forEach(connectionsModel::addElement);
        });
    }

    // mostra diálogo de conexão
    private void showConnectionDialog() {
        ConnectionDialog.ConnectionResult result = ConnectionDialog.showDialog(this);
        if (result != null) {
            torrent.connectToNode(result.address(), result.port());
        }
    }

    // classe para mostrar ficheiros com contagem de nós
    private static class FileSearchResultDisplay {
        private final List<FileSearchResult> results; // todos os nós que têm o ficheiro
        private final String fileName;
        private final long fileSize;

        public FileSearchResultDisplay(FileSearchResult result) {
            this.results = new ArrayList<>();
            this.results.add(result);
            this.fileName = result.fileName();
            this.fileSize = result.fileSize();
        }

        public void addResult(FileSearchResult result) {
            results.add(result); // adiciona mais um nó
        }

        public List<FileSearchResult> getAllResults() {
            return new ArrayList<>(results);
        }

        public int getNodeCount() {
            return results.size(); // número de nós que têm o ficheiro
        }

        @Override
        public String toString() {
            // formato: nome (tamanho) (X nodes)
            return String.format("%s (%d bytes) (%d nodes)",
                    fileName, fileSize, getNodeCount());
        }
    }
}