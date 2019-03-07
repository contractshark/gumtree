/*
 * This file is part of GumTree.
 *
 * GumTree is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GumTree is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GumTree.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2011-2015 Jean-Rémy Falleri <jr.falleri@gmail.com>
 * Copyright 2011-2015 Floréal Morandat <florealm@gmail.com>
 */

package com.github.gumtreediff.io;

import com.github.gumtreediff.actions.model.*;
import com.github.gumtreediff.io.TreeIoUtils.AbstractSerializer;
import com.github.gumtreediff.matchers.Mapping;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;
import com.google.gson.internal.Excluder;
import com.google.gson.stream.JsonWriter;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

public final class ActionsIoUtils {

    private ActionsIoUtils() {
    }

    public static ActionSerializer toText(TreeContext sctx, List<Action> actions,
                                          MappingStore mappings) throws IOException {
        return new ActionSerializer(sctx, mappings, actions) {

            @Override
            protected ActionFormatter newFormatter(TreeContext ctx, Writer writer) throws Exception {
                return new TextFormatter(ctx, writer);
            }
        };
    }

    public static ActionSerializer toXml(TreeContext sctx, List<Action> actions,
                                         MappingStore mappings) throws IOException {
        return new ActionSerializer(sctx, mappings, actions) {

            @Override
            protected ActionFormatter newFormatter(TreeContext ctx, Writer writer) throws Exception {
                return new XmlFormatter(ctx, writer);
            }
        };
    }

    public static ActionSerializer toJson(TreeContext sctx, List<Action> actions,
                                              MappingStore mappings) throws IOException {
        return new ActionSerializer(sctx, mappings, actions) {

            @Override
            protected ActionFormatter newFormatter(TreeContext ctx, Writer writer) throws Exception {
                return new JsonFormatter(ctx, writer);
            }
        };
    }

    public abstract static class ActionSerializer extends AbstractSerializer {
        final TreeContext context;
        final MappingStore mappings;
        final List<Action> actions;

        ActionSerializer(TreeContext context, MappingStore mappings, List<Action> actions) {
            this.context = context;
            this.mappings = mappings;
            this.actions = actions;
        }

        protected abstract ActionFormatter newFormatter(TreeContext ctx, Writer writer) throws Exception;

        @Override
        public void writeTo(Writer writer) throws Exception {
            ActionFormatter fmt = newFormatter(context, writer);
            // Start the output
            fmt.startOutput();

            // Write the matches
            fmt.startMatches();
            for (Mapping m: mappings) {
                fmt.match(m.getFirst(), m.getSecond());
            }
            fmt.endMatches();

            // Write the actions
            fmt.startActions();
            for (Action a : actions) {
                ITree src = a.getNode();
                if (a instanceof Move) {
                    ITree dst = mappings.getDst(src);
                    fmt.moveAction((Move) a, src, dst.getParent(), ((Move) a).getPosition());
                } else if (a instanceof Update) {
                    ITree dst = mappings.getDst(src);
                    fmt.updateAction((Update) a, src, dst);
                } else if (a instanceof Insert) {
                    ITree dst = a.getNode();
                    if (dst.isRoot())
                        fmt.insertRoot((Insert) a, src);
                    else
                        fmt.insertAction((Insert) a, src, dst.getParent(), dst.getParent().getChildPosition(dst));
                } else if (a instanceof Delete) {
                    fmt.deleteAction((Delete) a, src);
                } else if (a instanceof TreeInsert) {
                    ITree dst = a.getNode();
                    fmt.insertTreeAction((TreeInsert) a, src, dst.getParent(), dst.getParent().getChildPosition(dst));
                } else if (a instanceof  TreeDelete) {
                    fmt.deleteTreeAction((TreeDelete) a, src);
                }

            }
            fmt.endActions();

            // Finish up
            fmt.endOutput();
        }
    }

    interface ActionFormatter {
        void startOutput() throws Exception;

        void endOutput() throws Exception;

        void startMatches() throws Exception;

        void match(ITree srcNode, ITree destNode) throws Exception;

        void endMatches() throws Exception;

        void startActions() throws Exception;

        void insertRoot(Insert action, ITree node) throws Exception;

        void insertAction(Insert action, ITree node, ITree parent, int index) throws Exception;

        void insertTreeAction(TreeInsert action, ITree node, ITree parent, int index) throws Exception;

        void moveAction(Move action, ITree src, ITree dst, int index) throws Exception;

        void updateAction(Update action, ITree src, ITree dst) throws Exception;

        void deleteAction(Delete action, ITree node) throws Exception;

        void deleteTreeAction(TreeDelete action, ITree node) throws Exception;

        void endActions() throws Exception;
    }

    static class XmlFormatter implements ActionFormatter {
        final TreeContext context;
        final XMLStreamWriter writer;

        XmlFormatter(TreeContext context, Writer w) throws XMLStreamException {
            XMLOutputFactory f = XMLOutputFactory.newInstance();
            writer = new IndentingXMLStreamWriter(f.createXMLStreamWriter(w));
            this.context = context;
        }

        @Override
        public void startOutput() throws XMLStreamException {
            writer.writeStartDocument();
        }

        @Override
        public void endOutput() throws XMLStreamException {
            writer.writeEndDocument();
        }

        @Override
        public void startMatches() throws XMLStreamException {
            writer.writeStartElement("matches");
        }

        @Override
        public void match(ITree srcNode, ITree destNode) throws XMLStreamException {
            writer.writeEmptyElement("match");
            writer.writeAttribute("src", Integer.toString(srcNode.getId()));
            writer.writeAttribute("dest", Integer.toString(destNode.getId()));
        }

        @Override
        public void endMatches() throws XMLStreamException {
            writer.writeEndElement();
        }

        @Override
        public void startActions() throws XMLStreamException {
            writer.writeStartElement("actions");
        }

        @Override
        public void insertRoot(Insert action, ITree node) throws Exception {
            start(action, node);
            end(node);
        }

        @Override
        public void insertAction(Insert action, ITree node, ITree parent, int index) throws Exception {
            start(action, node);
            writer.writeAttribute("parent", Integer.toString(parent.getId()));
            writer.writeAttribute("at", Integer.toString(index));
            end(node);
        }

        @Override
        public void insertTreeAction(TreeInsert action, ITree node, ITree parent, int index) throws Exception {
            start(action, node);
            writer.writeAttribute("parent", Integer.toString(parent.getId()));
            writer.writeAttribute("at", Integer.toString(index));
            end(node);
        }

        @Override
        public void moveAction(Move action, ITree src, ITree dst, int index) throws XMLStreamException {
            start(action, src);
            writer.writeAttribute("parent", Integer.toString(dst.getId()));
            writer.writeAttribute("at", Integer.toString(index));
            end(src);
        }

        @Override
        public void updateAction(Update action, ITree src, ITree dst) throws XMLStreamException {
            start(action, src);
            writer.writeAttribute("label", dst.getLabel());
            end(src);
        }

        @Override
        public void deleteAction(Delete action, ITree node) throws Exception {
            start(action, node);
            end(node);
        }

        @Override
        public void deleteTreeAction(TreeDelete action, ITree node) throws Exception {
            start(action, node);
            end(node);
        }

        @Override
        public void endActions() throws XMLStreamException {
            writer.writeEndElement();
        }

        private void start(Action action, ITree src) throws XMLStreamException {
            writer.writeEmptyElement(action.getName());
            writer.writeAttribute("tree", Integer.toString(src.getId()));
        }

        private void end(ITree node) throws XMLStreamException {
//            writer.writeEndElement();
        }
    }

    static class TextFormatter implements ActionFormatter {
        final Writer writer;
        final TreeContext context;

        public TextFormatter(TreeContext ctx, Writer writer) {
            this.context = ctx;
            this.writer = writer;
        }

        @Override
        public void startOutput() throws Exception {
        }

        @Override
        public void endOutput() throws Exception {
        }

        @Override
        public void startMatches() throws Exception {
        }

        @Override
        public void match(ITree srcNode, ITree destNode) throws Exception {
            write(String.format("===\nmatch\n---\n%s\n%s", toS(srcNode), toS(destNode)));
        }

        @Override
        public void endMatches() throws Exception {
        }

        @Override
        public void startActions() throws Exception {
        }

        @Override
        public void insertRoot(Insert action, ITree node) throws Exception {
            write(action.format(context));
        }

        @Override
        public void insertAction(Insert action, ITree node, ITree parent, int index) throws Exception {
            write(action.format(context));
        }

        @Override
        public void insertTreeAction(TreeInsert action, ITree node, ITree parent, int index) throws Exception {
            write(action.format(context));
        }

        @Override
        public void moveAction(Move action, ITree src, ITree dst, int position) throws Exception {
            write(action.format(context));
        }

        @Override
        public void updateAction(Update action, ITree src, ITree dst) throws Exception {
            write(action.format(context));
        }

        @Override
        public void deleteAction(Delete action, ITree node) throws Exception {
            write(action.format(context));
        }

        @Override
        public void deleteTreeAction(TreeDelete action, ITree node) throws Exception {
            write(action.format(context));
        }

        @Override
        public void endActions() throws Exception {
        }

        private void write(String fmt, Object... objs) throws IOException {
            writer.append(fmt);
            writer.append("\n");
        }

        private String toS(ITree node) {
            return String.format("%s(%d)", node.toPrettyString(context), node.getId());
        }
    }

    static class JsonFormatter implements ActionFormatter {
        private final JsonWriter writer;

        JsonFormatter(TreeContext ctx, Writer writer) {

            this.writer = new JsonWriter(writer);
            this.writer.setIndent("  ");
        }

        @Override
        public void startOutput() throws IOException {
            writer.beginObject();
        }

        @Override
        public void endOutput() throws IOException {
            writer.endObject();
        }

        @Override
        public void startMatches() throws Exception {
            writer.name("matches").beginArray();
        }

        @Override
        public void match(ITree srcNode, ITree destNode) throws Exception {
            writer.beginObject();
            writer.name("src").value(srcNode.getId());
            writer.name("dest").value(destNode.getId());
            writer.endObject();
        }

        @Override
        public void endMatches() throws Exception {
            writer.endArray();
        }

        @Override
        public void startActions() throws IOException {
            writer.name("actions").beginArray();
        }

        @Override
        public void insertRoot(Insert action, ITree node) throws IOException {
            start(action, node);
            end(node);
        }

        @Override
        public void insertAction(Insert action, ITree node, ITree parent, int index) throws IOException {
            start(action, node);
            writer.name("parent").value(parent.getId());
            writer.name("at").value(index);
            end(node);
        }

        @Override
        public void insertTreeAction(TreeInsert action, ITree node, ITree parent, int index) throws IOException {
            start(action, node);
            writer.name("parent").value(parent.getId());
            writer.name("at").value(index);
            end(node);
        }

        @Override
        public void moveAction(Move action, ITree src, ITree dst, int index) throws IOException {
            start(action, src);
            writer.name("parent").value(dst.getId());
            writer.name("at").value(index);
            end(src);
        }

        @Override
        public void updateAction(Update action, ITree src, ITree dst) throws IOException {
            start(action, src);
            writer.name("label").value(dst.getLabel());
            end(src);
        }

        @Override
        public void deleteAction(Delete action, ITree node) throws IOException {
            start(action, node);
            end(node);
        }

        @Override
        public void deleteTreeAction(TreeDelete action, ITree node) throws IOException {
            start(action, node);
            end(node);
        }

        private void start(Action action, ITree src) throws IOException {
            writer.beginObject();
            writer.name("action").value(action.getName());
            writer.name("tree").value(src.getId());
        }

        private void end(ITree node) throws IOException {
            writer.endObject();
        }

        @Override
        public void endActions() throws Exception {
            writer.endArray();
        }
    }
}
