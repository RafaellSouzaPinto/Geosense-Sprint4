-- V20: Garantir dados em PATIO.NOME_UNIDADE para ambientes já existentes

DECLARE
    v_existe NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_existe
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'PATIO'
       AND COLUMN_NAME = 'NOME_UNIDADE';

    IF v_existe = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PATIO ADD (NOME_UNIDADE VARCHAR2(255))';
    END IF;
END;
/

BEGIN
    FOR rec IN (
        SELECT id, localizacao
          FROM patio
         WHERE nome_unidade IS NULL
            OR TRIM(nome_unidade) IS NULL
    ) LOOP
        UPDATE patio
           SET nome_unidade = NVL(rec.localizacao, 'UNIDADE ' || rec.id)
         WHERE id = rec.id;
    END LOOP;
    COMMIT;
END;
/

SELECT 'Patio.NOME_UNIDADE verificado e preenchido.' AS STATUS FROM DUAL;


