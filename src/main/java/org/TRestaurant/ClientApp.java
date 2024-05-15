package org.TRestaurant;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

public class ClientApp {
    private static final ArrayList<String> orderList = new ArrayList<>();
    private static final JLabel orderLabel = new JLabel("Захиалах: ");
    private static final JTextField orderTextField = new JTextField(20);

    public static void main(String[] args) {
        JFrame frame = new JFrame("TRestaurant");
        frame.setSize(600, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new FlowLayout());

        JButton hotDogButton = new JButton("Hot-Dog");
        JButton burgerButton = new JButton("Burger");
        JButton pizzaButton = new JButton("Pizza");
        JButton confirmButton = new JButton("Confirm Order");

        hotDogButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                orderList.add("Hot-Dog");
                updateOrderLabel();
            }
        });

        burgerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                orderList.add("Burger");
                updateOrderLabel();
            }
        });

        pizzaButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                orderList.add("Pizza");
                updateOrderLabel();
            }
        });

        confirmButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendOrderToServer(orderList);
            }
        });

        JPanel top = new JPanel(new GridLayout(1, 3, 10, 10));
        top.add(hotDogButton);
        top.add(burgerButton);
        top.add(pizzaButton);
        frame.add(top);
        frame.add(confirmButton);
        frame.add(orderLabel);
        //frame.add(orderTextField);

        frame.setVisible(true);

        subscribeToOrderUpdates();
    }

    private static void updateOrderLabel() {
        orderLabel.setText("Захиалах: " + String.join(", ", orderList));
        orderTextField.setText(String.join(", ", orderList));
    }

    private static void sendOrderToServer(ArrayList<String> orderList) {
        try (Socket socket = new Socket("localhost", 12345);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            out.writeObject(orderList);
            JOptionPane.showMessageDialog(null, "Order sent to the server!");
            orderList.clear();
            updateOrderLabel();
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to send order to the server.");
        }
    }

    private static void subscribeToOrderUpdates() {
        new Thread(() -> {
            try (Socket socket = new Socket("localhost", 12345);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                out.writeObject("subscribeToOrderUpdates");

                while (true) {
                    Object object = in.readObject();
                    if (object instanceof String && object.equals("orderUpdate")) {
                        int orderId = in.readInt();
                        String status = (String) in.readObject();
                        String orderDetails = (String) in.readObject();
                        handleOrderUpdate(orderId, status, orderDetails);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void handleOrderUpdate(int orderId, String status, String orderDetails) {
        SwingUtilities.invokeLater(() -> {
            boolean orderFound = false;
            for (String item : orderList) {
                if (orderDetails.contains(item)) {
                    orderFound = true;
                    break;
                }
            }

            if (orderFound) {
                JOptionPane.showMessageDialog(null, "Your order (" + orderDetails + ") has been " + status.toLowerCase() + "!");
            } else {
                JOptionPane.showMessageDialog(null, "Order " + orderId + " (" + orderDetails + ") has been " + status.toLowerCase() + ".");
            }
        });
    }
}