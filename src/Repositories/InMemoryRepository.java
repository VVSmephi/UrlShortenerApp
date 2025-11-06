package Repositories;

import Models.ShortLink;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRepository {
    private final ConcurrentHashMap<String, ShortLink> map = new ConcurrentHashMap<>();

    public boolean exists(String id) { return map.containsKey(id); }

    public ShortLink get(String id) { return map.get(id); }

    public void save(ShortLink l) { map.put(l.id(), l); }

    public void delete(String id) { map.remove(id); }

    public List<ShortLink> findByOwner(UUID owner) {
        List<ShortLink> res = new ArrayList<>();
        for (ShortLink l : map.values()) if (l.owner().equals(owner)) res.add(l);
        res.sort(Comparator.comparing(ShortLink::createdAt).reversed());
        return res;
    }

    public Collection<ShortLink> findAll() { return map.values(); }
}
