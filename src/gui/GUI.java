package gui;

import pt.iscte.pcd.isctorrent.core.IscTorrent;
import pt.iscte.pcd.isctorrent.gui.dialogs.ConnectionDialog;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class GUI extends JFrame {
    private final IscTorrent torrent;
    private final JTextField searchField;
    private final JList<FileSearchResult> resultsList;
    private final DefaultListModel<FileSearchResult> resultsModel;

    public GUI(IscTorrent torrent, int port) {
        this.torrent = torrent;
        setTitle("IscTorrent - Porta: " + port);
        setSize(600, 400);
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

        // Botões
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        JButton downloadButton = new JButton("Transferir");
        JButton connectButton = new JButton("Conectar");
        buttonPanel.add(downloadButton);
        buttonPanel.add(connectButton);

        // Layout principal
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mainPanel.add(searchPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.EAST);

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
        List<FileSearchResult> selectedItems = resultsList.getSelectedValuesList();
        if (!selectedItems.isEmpty()) {
            for (FileSearchResult selected : selectedItems) {
                torrent.startDownload(selected);
            }
        }
    }

    public synchronized void addSearchResults(List<FileSearchResult> results) {
        SwingUtilities.invokeLater(() -> {
            for (FileSearchResult result : results) {
                // Verificar se já existe um resultado igual no modelo
                boolean isDuplicate = false;
                for (int i = 0; i < resultsModel.size(); i++) {
                    FileSearchResult existing = resultsModel.getElementAt(i);
                    if (existing.hash().equals(result.hash())) {
                        isDuplicate = true;
                        break;
                    }
                }

                if (!isDuplicate) {
                    resultsModel.addElement(result);
                }
            }
        });
    }

    private void showConnectionDialog() {
        ConnectionDialog.ConnectionResult result = ConnectionDialog.showDialog(this);
        if (result != null) {
            torrent.connectToNode(result.address(), result.port());
        }
    }
}