package dev.ked.silkroad.contracts;

public enum ContractState {
    POSTED,       // Contract created, waiting for transporter
    ACCEPTED,     // Transporter accepted, awaiting pickup
    IN_TRANSIT,   // Cargo picked up, transporter traveling
    DELIVERED,    // Delivered to trade post
    COMPLETED,    // Buyer claimed items
    CANCELLED,    // Cancelled by system or admin
    EXPIRED       // Bounty decayed to $0
}
