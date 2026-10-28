package io.github.vbaggio.sefaz.mock.xsd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.XMLConstants;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Validação opcional do conteúdo de {@code nfeDadosMsg} contra XSDs fornecidos pelo usuário em
 * {@code SEFAZ_XSD_DIR} (ADR-001). Sem diretório, nada é validado.
 */
public final class XsdValidator {

    private final Path directory;
    private final Map<String, Schema> schemas = new ConcurrentHashMap<>();

    public XsdValidator(Path directory) {
        this.directory = directory;
    }

    public static XsdValidator disabled() {
        return new XsdValidator(null);
    }

    public boolean enabled() {
        return directory != null;
    }

    /** Mensagem de erro se o conteúdo não passar no XSD; vazio se passar ou se não houver XSD. */
    public Optional<String> validate(Element content, String xsdFileName) {
        if (directory == null || xsdFileName == null) {
            return Optional.empty();
        }
        Path file = directory.resolve(xsdFileName);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            Validator validator = schemas.computeIfAbsent(xsdFileName, n -> load(file)).newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new DOMSource(content));
            return Optional.empty();
        } catch (SAXException e) {
            return Optional.of(e.getMessage());
        } catch (IOException e) {
            return Optional.of("Falha ao ler XSD " + xsdFileName + ": " + e.getMessage());
        }
    }

    private static Schema load(Path file) {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            // Os XSDs vêm do usuário e importam outros arquivos do mesmo diretório.
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "file");
            return factory.newSchema(file.toFile());
        } catch (SAXException e) {
            throw new IllegalStateException("XSD inválido: " + file + ": " + e.getMessage(), e);
        }
    }
}
