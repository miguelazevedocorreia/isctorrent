package pt.iscte.pcd.isctorrent.gui;

import pt.iscte.pcd.isctorrent.concurrency.MyLock;
import pt.iscte.pcd.isctorrent.core.IscTorrent;
import pt.iscte.pcd.isctorrent.gui.dialogs.ConnectionDialog;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GUI extends JFrame {
    private final IscTorrent torrent;
    private final JTextField searchField;
    private final JList<Object> resultsList;
    private final DefaultListModel<Object> resultsModel;
    private final MyLock lock = new MyLock();

    public GUI(IscTorrent torrent, int port) {
        this.torrent = torrent;
        setTitle("IscTorrent - Porta: " + port);
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchField = new JTextField();
        JButton searchButton = new JButton("Procurar");
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        resultsModel = new DefaultListModel<>();
        resultsList = new JList<>(resultsModel);
        resultsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        resultsList.setCellRenderer(new FileSearchResultRenderer());
        JScrollPane scrollPane = new JScrollPane(resultsList);

        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        JButton downloadButton = new JButton("Transferir");
        JButton connectButton = new JButton("Conectar");
        buttonPanel.add(downloadButton);
        buttonPanel.add(connectButton);

        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mainPanel.add(searchPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.EAST);

        setContentPane(mainPanel);

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
        List<Object> selectedItems = resultsList.getSelectedValuesList();
        if (!selectedItems.isEmpty()) {
            for (Object selected : selectedItems) {
                FileSearchResult result = null;
                if (selected instanceof FileSearchResultDisplay) {
                    result = ((FileSearchResultDisplay) selected).getResult();
                } else if (selected instanceof FileSearchResult) {
                    result = (FileSearchResult) selected;
                }

                if (result != null) {
                    torrent.startDownload(result);
                }
            }
        }
    }

    public void addSearchResults(List<FileSearchResult> results) {
        SwingUtilities.invokeLater(() -> {
            lock.lock();
            try {
                // Agrupar resultados por arquivo
                Map<String, List<FileSearchResult>> groupedResults = results.stream()
                        .collect(Collectors.groupingBy(FileSearchResult::fileName));

                for (Map.Entry<String, List<FileSearchResult>> entry : groupedResults.entrySet()) {
                    List<FileSearchResult> fileResults = entry.getValue();
                    if (!fileResults.isEmpty()) {
                        // Usar o primeiro resultado como base
                        FileSearchResult first = fileResults.get(0);

                        // Criar wrapper para exibição
                        FileSearchResultDisplay display = new FileSearchResultDisplay(first, fileResults);

                        // Verificar se já existe
                        boolean exists = false;
                        for (int i = 0; i < resultsModel.size(); i++) {
                            Object element = resultsModel.getElementAt(i);
                            if (element instanceof FileSearchResultDisplay) {
                                FileSearchResultDisplay existingDisplay = (FileSearchResultDisplay) element;
                                if (existingDisplay.getResult().fileName().equals(first.fileName())) {
                                    exists = true;
                                    break;
                                }
                            } else if (element instanceof FileSearchResult) {
                                FileSearchResult existing = (FileSearchResult) element;
                                if (existing.fileName().equals(first.fileName())) {
                                    exists = true;
                                    break;
                                }
                            }
                        }

                        if (!exists) {
                            resultsModel.addElement(display);
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        });
    }

    private void showConnectionDialog() {
        ConnectionDialog.ConnectionResult result = ConnectionDialog.showDialog(this);
        if (result != null) {
            torrent.connectToNode(result.address(), result.port());
        }
    }

    // Renderer customizado para a lista
    private static class FileSearchResultRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof FileSearchResultDisplay) {
                setText(((FileSearchResultDisplay) value).toString());
            } else if (value instanceof FileSearchResult) {
                setText(value.toString());
            }
            return this;
        }
    }

    // Classe wrapper para exibir resultados agrupados
    private static class FileSearchResultDisplay {
        private final FileSearchResult result;
        private final List<FileSearchResult> allResults;

        public FileSearchResultDisplay(FileSearchResult result, List<FileSearchResult> allResults) {
            this.result = result;
            this.allResults = allResults;
        }

        public FileSearchResult getResult() {
            return result;
        }

        @Override
        public String toString() {
            String allPorts = allResults.stream()
                    .map(r -> String.valueOf(r.nodePort()))
                    .distinct()
                    .collect(Collectors.joining(" / "));

            return String.format("%s (%d bytes) - (%d nós) - (Porta: %s)",
                    result.fileName(),
                    result.fileSize(),
                    allResults.size(),
                    allPorts);
        }
    }
}