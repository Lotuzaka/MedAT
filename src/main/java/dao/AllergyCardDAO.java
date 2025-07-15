package dao;

import model.AllergyCardData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/** DAO for persisting allergy cards. */
public class AllergyCardDAO {
    private final Connection conn;

    public AllergyCardDAO(Connection conn) {
        this.conn = conn;
    }

    public void insertAll(List<AllergyCardData> cards, int sessionId) throws SQLException {
        String sql = "INSERT INTO allergy_card(test_session_id,idx,name,dob,medication,blood_group,allergies,card_no,country,image_png) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i=0;i<cards.size();i++) {
                AllergyCardData d = cards.get(i);
                ps.setInt(1, sessionId);
                ps.setInt(2, i);
                ps.setString(3, d.name());
                if(d.geburtsdatum()!=null) {
                    ps.setDate(4, java.sql.Date.valueOf(d.geburtsdatum()));
                } else {
                    ps.setDate(4, null);
                }
                ps.setString(5, d.medikamenteneinnahme());
                ps.setString(6, d.blutgruppe());
                ps.setString(7, d.bekannteAllergien());
                ps.setString(8, d.ausweisnummer());
                ps.setString(9, d.ausstellungsland());
                ps.setBytes(10, d.bildPng());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
