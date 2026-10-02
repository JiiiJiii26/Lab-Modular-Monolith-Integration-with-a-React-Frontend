package edu.cit.pena.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ChannelOrderRepository extends JpaRepository<ChannelOrder, String> {
    List<ChannelOrder> findByStatusOrderByPlacedAtAsc(String status);
}
