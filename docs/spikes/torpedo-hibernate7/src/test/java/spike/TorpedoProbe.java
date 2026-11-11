package spike;

import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;
import org.torpedoquery.jakarta.jpa.Torpedo;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TorpedoProbe {
    @Entity(name="ProbeItem") public static class Item {
        @Id @GeneratedValue public Long id;
        public String name;
        @ManyToOne public Group itemGroup;
        public Item() {}
        Item(String name) { this.name=name; }
        public Long getId(){return id;}
        public String getName(){return name;}
        public Group getItemGroup(){return itemGroup;}
    }
    @Entity(name="ProbeGroup") public static class Group {
        @Id @GeneratedValue public Long id;
        public String name;
        public Group() {}
        Group(String name) { this.name=name; }
        public String getName(){return name;}
    }

    @Test void filterAndCountOnRealMySql() {
        try (MySQLContainer mysql = new MySQLContainer("mysql:8.4.11")) {
            mysql.start();
            EntityManagerFactory factory = Persistence.createEntityManagerFactory("spike", Map.of(
                "jakarta.persistence.jdbc.url", mysql.getJdbcUrl(), "jakarta.persistence.jdbc.user", mysql.getUsername(),
                "jakarta.persistence.jdbc.password", mysql.getPassword(), "hibernate.hbm2ddl.auto", "create-drop",
                "hibernate.dialect", "spike.ProbeDialect"));
            try {
                EntityManager em = factory.createEntityManager();
                try {
                    em.getTransaction().begin(); Group group = new Group("farm"); em.persist(group);
                    Item first = new Item("alpha"); first.itemGroup = group; em.persist(first);
                    Item second = new Item("beta"); second.itemGroup = group; em.persist(second); em.getTransaction().commit(); em.clear();
                    Item alias = Torpedo.from(Item.class);
                    Torpedo.where(alias.getName()).eq("alpha");
                    assertEquals(1, Torpedo.select(alias).list(em).size());
                    Item countAlias = Torpedo.from(Item.class);
                    assertEquals(2L, Torpedo.select(Torpedo.count(countAlias)).get(em).orElseThrow());
                    Item page = Torpedo.from(Item.class);
                    Torpedo.orderBy(Torpedo.asc(page.getName()));
                    assertEquals("beta", Torpedo.select(page).setFirstResult(1).setMaxResults(1).list(em).get(0).getName());
                    Item joined = Torpedo.from(Item.class);
                    Torpedo.where(joined.getItemGroup().getName()).eq("farm");
                    assertEquals(2, Torpedo.select(joined).list(em).size());
                    assertEquals("alpha,beta", em.createQuery("select GROUP_CONCAT(i.name) from ProbeItem i", String.class).getSingleResult());
                } finally { em.close(); }
            } finally { factory.close(); }
        }
    }
}
