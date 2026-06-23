import com.razorpay.RazorpayClient;
import org.json.JSONObject;

public class TestRazorpay {
    public static void main(String[] args) {
        try {
            String keyId = "rzp_test_T2gYo27GSiwjcg";
            String keySecret = "r2oeTLwW5nSf00z3FRSxGd5R";
            RazorpayClient razorpay = new RazorpayClient(keyId, keySecret);
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", 5000);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "txn_123");
            
            com.razorpay.Order order = razorpay.orders.create(orderRequest);
            System.out.println("SUCCESS: " + order.get("id"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
