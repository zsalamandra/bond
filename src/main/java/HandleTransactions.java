import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import model.ObjectType;
import model.Transaction;
import model.TransactionIncomeInfo;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class HandleTransactions {

    private final static BigDecimal NOMINAL = BigDecimal.valueOf(1000);

    public void handle() {
        List<Transaction> onePeriodTransactions = readJson("one-period-transactions.json");
        List<Transaction> secondPeriodTransactions = readJson("second-period-transactions.json");

        Map<String, List<Transaction>> transactionsByPeriod = Map.of(
                "one", onePeriodTransactions,
                "second", secondPeriodTransactions
        );

        List<TransactionIncomeInfo> transactionIncomeInfos = transactionsByPeriod.values().stream()
                .map(this::getIncomes)
                .flatMap(Collection::stream)
                .toList();

        transactionIncomeInfos.forEach(this::printTr);
    }

    private List<TransactionIncomeInfo> getIncomes(List<Transaction> transactions) {

        if (Objects.isNull(transactions) || transactions.isEmpty()) {
            return Collections.emptyList();
        }

        List<TransactionIncomeInfo> transactionIncomeInfos = transactions.stream()
                .sorted(Comparator.comparing(Transaction::getCreatedAt))
                .map(tr -> {
                    return TransactionIncomeInfo.builder()
                            .id(tr.getId())
                            .objectType(tr.getObjectType())
                            .productCount(Math.max(tr.getOutgoingProductCount(), tr.getIncomingProductCount()))
                            .amount(tr.getIncomingAmount().max(tr.getOutgoingAmount()))
                            .createdAt(tr.getCreatedAt())
                            .build();
                }).collect(Collectors.toList());

        handleTransactions(transactionIncomeInfos);

        return transactionIncomeInfos.stream()
                .filter(it -> it.getObjectType().equals(ObjectType.REGISTRY_RECORD) || it.getObjectType().equals(ObjectType.SELL))
                .collect(Collectors.toList());
    }

    private void printTr(TransactionIncomeInfo transaction) {

        String format = String.format("Date: %s, Amount: %s, count: %d, Type: %s", transaction.getCreatedAt().toString(), transaction.getAmount().toString(), transaction.getProductCount(), transaction.getObjectType().toString());
        System.out.println(format);
    }

    private void handleTransactions(List<TransactionIncomeInfo> transactions) {

        ListIterator<TransactionIncomeInfo> iterator = transactions.listIterator();

        while (iterator.hasNext()) {

            TransactionIncomeInfo currentTransaction = iterator.next();

            if (currentTransaction.getObjectType() == ObjectType.REGISTRY_RECORD) {

                BigDecimal sumOfPurchaseNkd = getSumOfPurchaseNkd(transactions, iterator.previousIndex());
                currentTransaction.setAmount(currentTransaction.getAmount().subtract(sumOfPurchaseNkd));

            } else if (currentTransaction.getObjectType() == ObjectType.SELL) {

                deductPurchaseCostsFromSales(transactions, iterator.previousIndex(), currentTransaction);
            }
        }
    }

    private void deductPurchaseCostsFromSales(List<TransactionIncomeInfo> transactions, int index, TransactionIncomeInfo sellTransaction) {

        ListIterator<TransactionIncomeInfo> iterator = transactions.listIterator();

        boolean purchaseWasFound = false;

        while (iterator.hasNext() && iterator.nextIndex() < index) {

            TransactionIncomeInfo transaction = iterator.next();

            int incomingProductCount = transaction.getProductCount();

            // если транзакция имеет тип отличный от PURCHASE или это обнуленная транзакция, переходим на следующую итерацию
            if (transaction.getObjectType() != ObjectType.PURCHASE || incomingProductCount == 0) {
                continue;
            }

            purchaseWasFound = true;

            // цена за одну облигацию
            BigDecimal priceForOne = transaction.getAmount().divide(BigDecimal.valueOf(incomingProductCount), BigDecimal.ROUND_HALF_UP);

            // выбираем какое кол-во облигаций вычтем с данной PURCHASE облигации:
            // так как, можем вычесть равное или меньшее кол-во облигаций в данной PURCHASE тр-ции
            // выбираем меньшее
            int sellCount = sellTransaction.getProductCount();
            int chosenSellCount = Math.min(sellCount, incomingProductCount);

            // себестоимость выбранного кол-ва облигаций
            BigDecimal purchase = priceForOne.multiply(BigDecimal.valueOf(chosenSellCount));

            // корректируем обработанный PURCHASE
            transaction.setAmount(transaction.getAmount().subtract(purchase));
            transaction.setProductCount(incomingProductCount - chosenSellCount);

            // обновляем сумму и кол-во продажной (SELL) транзакции
            sellTransaction.setProductCount(sellCount - chosenSellCount);
            sellTransaction.setAmount(sellTransaction.getAmount().subtract(purchase));

            // выходим из цикла, если нашли все PURCHASE транзакции для вычета себестоимости
            if (sellTransaction.getProductCount() == 0) {
                break;
            }
        }

        if (!purchaseWasFound) {
            // обновляем сумму и кол-во продажной (SELL) транзакции, когда не нашли PURCHASE транзакцию
            BigDecimal sumOfNominal = NOMINAL.multiply(BigDecimal.valueOf(sellTransaction.getProductCount()));
            sellTransaction.setAmount(sellTransaction.getAmount().subtract(sumOfNominal));
            sellTransaction.setProductCount(0);
        }
    }

    private BigDecimal getSumOfPurchaseNkd(List<TransactionIncomeInfo> transactions, int index) {

        BigDecimal sum = BigDecimal.ZERO;
        boolean purchaseFound = false;

        // Создаём ListIterator начиная с указанного индекса
        ListIterator<TransactionIncomeInfo> iterator = transactions.listIterator(index);

        while (iterator.hasPrevious()) { // Пока есть предыдущие элементы
            TransactionIncomeInfo transaction = iterator.previous(); // Получаем предыдущий элемент

            if (transaction.getObjectType() == ObjectType.PURCHASE) {
                sum = sum.add(transaction.getAmount().subtract(NOMINAL.multiply(BigDecimal.valueOf(transaction.getProductCount()))));

                // обнуляем сумму и кол-во, чтоб данная транзакция не участвовала в следующих расчетах транзакциях
                transaction.setAmount(BigDecimal.ZERO);
                transaction.setProductCount(0);

                purchaseFound = true;
            } else if (purchaseFound) { // Прерываем цикл, если после нахождения PURCHASE встретился другой тип транзакции
                break;
            }
        }

        return sum;
    }

    private List<Transaction> readJson(String filename) {

        InputStream inputStream = Main.class.getClassLoader().getResourceAsStream(filename);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        try {
            // Чтение и парсинг JSON из файла
            List<Transaction> transactions = mapper.readValue(inputStream, new TypeReference<List<Transaction>>() {
            });
            System.out.println("Успешно загружено " + transactions.size() + " транзакций.");

            return transactions;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
