package co.proxydemo.service;

public interface PaymentProvider extends PaymentService {
    String getProviderKey();
}
