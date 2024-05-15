package org.TRestaurant;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ServerApp {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/restaurantdb";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "admin";
    private static final Set<Socket> subscribedClients = new HashSet<>();

    private static String orderDetails = null;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("Server started, waiting for connections...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

                Object object = in.readObject();
                if (object instanceof ArrayList) {
                    ArrayList<String> orderList = (ArrayList<String>) object;
                    saveOrderToDatabase(orderList);
                } else if (object instanceof String && object.equals("getOrders")) {
                    List<Order> orders = getOrdersFromDatabase();
                    out.writeObject(orders);
                } else if (object instanceof String && object.equals("confirmOrder")) {
                    int orderId = in.readInt();
                    boolean success = confirmOrder(orderId);
                    out.writeObject(success ? "success" : "failure");
                    if (success) {
                        notifySubscribedClients(orderId, "Confirmed");
                    }
                } else if (object instanceof String && object.equals("subscribeToOrderUpdates")) {
                    subscribedClients.add(socket);
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        private void saveOrderToDatabase(ArrayList<String> orderList) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String sql = "INSERT INTO orders (order_details, status) VALUES (?, 'Pending')";
                PreparedStatement statement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, String.join(", ", orderList));
                statement.executeUpdate();

                ResultSet generatedKeys = statement.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int orderId = generatedKeys.getInt(1);
                    System.out.println("New order saved with ID: " + orderId);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private List<Order> getOrdersFromDatabase() {
            List<Order> orders = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String sql = "SELECT * FROM orders";
                PreparedStatement statement = conn.prepareStatement(sql);
                ResultSet rs = statement.executeQuery();

                while (rs.next()) {
                    int id = rs.getInt("id");
                    String details = rs.getString("order_details");
                    String status = rs.getString("status");
                    orders.add(new Order(id, details, status));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return orders;
        }

        private boolean confirmOrder(int orderId) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String sql = "SELECT order_details FROM orders WHERE id = ?";
                PreparedStatement statement = conn.prepareStatement(sql);
                statement.setInt(1, orderId);
                ResultSet rs = statement.executeQuery();

                if (rs.next()) {
                    orderDetails = rs.getString("order_details");
                } else {
                    orderDetails = null;
                }

                sql = "UPDATE orders SET status = 'Confirmed' WHERE id = ?";
                statement = conn.prepareStatement(sql);
                statement.setInt(1, orderId);
                int rowsUpdated = statement.executeUpdate();
                if (rowsUpdated > 0 && orderDetails != null) {
                    notifySubscribedClients(orderId, "Confirmed");
                }
                return rowsUpdated > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        private void notifySubscribedClients(int orderId, String status) {
            Set<Socket> socketsToRemove = new HashSet<>();
            for (Socket socket : subscribedClients) {
                try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                    out.writeObject("orderUpdate");
                    out.writeInt(orderId);
                    out.writeObject(status);
                    out.writeObject(orderDetails);
                } catch (IOException e) {
                    socketsToRemove.add(socket);
                    e.printStackTrace();
                }
            }
            subscribedClients.removeAll(socketsToRemove);
        }
    }
}