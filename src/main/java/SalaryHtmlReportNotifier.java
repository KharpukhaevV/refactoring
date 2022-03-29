import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.time.LocalDate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

public class SalaryHtmlReportNotifier {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(SalaryHtmlReportNotifier.class);
    private Connection connection;

    public SalaryHtmlReportNotifier(Connection databaseConnection) {
        this.connection = databaseConnection;
    }

    public void generateAndSendHtmlSalaryReport(String departmentId, LocalDate dateFrom,
                                                LocalDate dateTo, String recipients) {
        try {
            // подготовить оператор с sql
            PreparedStatement ps = connection.prepareStatement("select emp.id as emp_id, emp.name as amp_name, sum(salary)as salary from employee emp left join" +
                    "salary_payments sp on emp.id = sp.employee_id where emp.department_id = ? and " +
                    " sp.date >= ? and sp.date <= ? group by emp.id, emp.name");
            // ввести параметры в sql
            ps.setString(0, departmentId);
            ps.setDate(1, new java.sql.Date(dateFrom.toEpochDay()));
            ps.setDate(2, new java.sql.Date(dateTo.toEpochDay()));
            // выполнить запрос и получить результат
            ResultSet results = ps.executeQuery();
            // создать StringBuilder, содержащий результирующий html
            StringBuilder resultingHtml = new StringBuilder();

            resultingHtml.append("<html><body><table><tr><td>Employee</td><td>Salary</td></tr>");
            double totals = 0;
            while (results.next()) {
                //формат зарплаты и налогов
                double salary = results.getDouble("salary");
                String formattedSalary;
                if (salary > 200000) {
                    formattedSalary = salary + " (taxes:" + salary * 0.2 + ")";
                } else if (salary < 200000 * 0.1) {
                    formattedSalary = "" + salary;
                } else {
                    formattedSalary = salary + " (taxes: " + salary * 0.13 + ")";
                }
                // обрабатывать каждую строку результатов запроса
                resultingHtml.append("<tr>"); // add row start tag

                resultingHtml.append("<td>").append(results.getString("emp_name")).append("</td>");
                //добавление имени сотрудника
                resultingHtml.append("<td>").append(formattedSalary).append("</td>");
                //добавление зарплаты работника за период
                resultingHtml.append("</tr>"); // добавить тег конца строки
                totals += results.getDouble("salary"); // добавить зарплату к сумме
            }

            resultingHtml.append("<tr><td>Total</td><td>").append(totals).append("</td></tr>");
            resultingHtml.append("</table></body></html>");
            // теперь, когда отчет построен, нам нужно отправить его в список получателей
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            // мы собираемся использовать почту Google, чтобы отправить это сообщение
            mailSender.setHost("mail.google.com");
            // построить сообщение
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(recipients);
            // установка текста сообщения, последний параметр 'true' говорит, что это формат HTML
            helper.setText(resultingHtml.toString(), true);
            helper.setSubject("Monthly department salary report");
            // отправить сообщение
            mailSender.send(message);
        } catch (SQLException e) {
            LOGGER.error("SQL Exception" + e);
            e.printStackTrace();
        } catch (MessagingException e) {
            LOGGER.error("Messaging Exception " + e);
            e.printStackTrace();
        }
    }
}