package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
final class CheckKeysTableService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CheckKeysTableService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        List<String> listIndexesPath = jdbcTemplate.query("show keys FROM page where column_name='path'",
                (resultSet, rowNum) -> {
                    return resultSet.getString("Column_name");
                });
        if (listIndexesPath.isEmpty()) {
            jdbcTemplate.execute("alter table page add key (path(50))");
        }
    }

}
