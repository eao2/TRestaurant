package org.TRestaurant;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminApp {
    private static JList<Order> ordersList;
    private static DefaultListModel<Order> listModel;
    private static ExecutorService executorService;
    private static Timer timer;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Admin");
            frame.setSize(600, 400);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            listModel = new DefaultListModel<>();
            ordersList = new JList<>(listModel);

            JButton confirmButton = new JButton("Захиалга батлах");
            confirmButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    Order selectedOrder = ordersList.getSelectedValue();
                    if (selectedOrder != null) {
                        confirmOrder(selectedOrder);
                    } else {
                        JOptionPane.showMessageDialog(frame, "Захиалга сонгогдоогүй байна.");
                    }
                }
            });

            frame.add(new JScrollPane(ordersList), BorderLayout.CENTER);
            frame.add(confirmButton, BorderLayout.SOUTH);

            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if (executorService != null) {
                        executorService.shutdown();
                    }
                    if (timer != null) {
                        timer.cancel();
                    }
                    System.exit(0);
                }
            });

            frame.setVisible(true);

            executorService = Executors.newSingleThreadExecutor();
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    refreshOrders();
                }
            }, 0, 5000); // Poll every 5 seconds
        });
    }

    private static void refreshOrders() {
        executorService.execute(() -> {
            try (Socket socket = new Socket("localhost", 12345);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                out.writeObject("getOrders");

                List<Order> orders = (List<Order>) in.readObject();
                SwingUtilities.invokeLater(() -> {
                    listModel.clear();
                    for (Order order : orders) {
                        if (order.getStatus().equals("Pending")) {
                            listModel.addElement(order);
                        }
                    }
                });
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Failed to refresh orders: " + e.getMessage()));
            }
        });
    }

    private static void confirmOrder(Order order) {
        executorService.execute(() -> {
            try (Socket socket = new Socket("localhost", 12345);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                out.writeObject("confirmOrder");
                out.writeInt(order.getId());
                out.flush(); // Flush the output stream

                String response = (String) in.readObject();
                if ("success".equals(response)) {
                    SwingUtilities.invokeLater(() -> {
                        listModel.removeElement(order);
                        JOptionPane.showMessageDialog(null, "Захиалга баталгаажсан!");
                    });
                } else {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Failed to confirm order."));
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Failed to confirm order: " + e.getMessage()));
            }
        });
    }
}
